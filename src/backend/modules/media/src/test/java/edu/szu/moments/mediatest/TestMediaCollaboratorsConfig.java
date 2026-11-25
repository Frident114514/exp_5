package edu.szu.moments.mediatest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import edu.szu.moments.posts.*;
import edu.szu.moments.posts.PostModels.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class TestMediaCollaboratorsConfig {
    @Bean
    @Primary
    MutableClock testMutableClock() {
        return new MutableClock(Instant.parse("2026-11-18T10:00:00Z"));
    }

    @Bean
    Clock mediaClock(@Qualifier("testMutableClock") MutableClock clock) {
        return clock;
    }

    @Bean
    Clock postsClock(@Qualifier("testMutableClock") MutableClock clock) {
        return clock;
    }

    @Bean
    TestCollaborators testCollaborators(JdbcTemplate jdbc) {
        return new TestCollaborators(jdbc);
    }

    public static final class MutableClock extends Clock {
        private volatile Instant current;

        public MutableClock(Instant initial) {
            this.current = initial;
        }

        public void set(Instant instant) {
            this.current = instant;
        }

        public void advance(Duration duration) {
            this.current = this.current.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }

    public static class TestCollaborators implements IdentityPort, RatingPort, AdminAuditPort {
        private final JdbcTemplate jdbc;
        private final ThreadLocal<Actor> currentActor = new ThreadLocal<>();

        public TestCollaborators(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        public void as(long id, Role role) {
            currentActor.set(new Actor(id, role));
        }

        public void logout() {
            currentActor.remove();
        }

        @Override
        public Actor requireActor() {
            Actor actor = currentActor.get();
            if (actor == null) {
                throw PostFailure.unauthenticated();
            }
            checkUser(actor, false);
            return actor;
        }

        @Override
        public void lockActiveActor(Actor actor) {
            checkUser(actor, true);
        }

        private void checkUser(Actor actor, boolean lock) {
            var rows = jdbc.queryForList(
                "SELECT role, status FROM users WHERE id = ?" + (lock ? " FOR UPDATE" : ""),
                actor.id()
            );
            if (rows.isEmpty()
                    || !"ACTIVE".equals(rows.getFirst().get("status"))
                    || !actor.role().name().equals(rows.getFirst().get("role"))) {
                throw PostFailure.unauthenticated();
            }
        }

        @Override
        public Map<Long, RatingSummary> summarize(Set<Long> ids) {
            Map<Long, RatingSummary> map = new HashMap<>();
            ids.forEach(id -> map.put(id, RatingSummary.empty()));
            if (!ids.isEmpty()) {
                String in = String.join(",", Collections.nCopies(ids.size(), "?"));
                jdbc.query(
                    "SELECT post_id, AVG(score) average, COUNT(*) amount FROM fixture_ratings WHERE post_id IN (" + in + ") GROUP BY post_id",
                    rs -> {
                        BigDecimal avg = rs.getBigDecimal("average").setScale(1, RoundingMode.HALF_UP);
                        map.put(rs.getLong("post_id"), new RatingSummary(avg, rs.getLong("amount")));
                    },
                    ids.toArray()
                );
            }
            return map;
        }

        @Override
        public void recordDeletePost(long adminId, long postId, Instant at) {
            jdbc.update(
                "INSERT INTO fixture_audit(admin_id, post_id, created_at) VALUES (?, ?, ?)",
                adminId, postId, Timestamp.from(at)
            );
        }
    }
}
