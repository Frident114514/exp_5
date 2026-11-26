package edu.szu.moments.interaction;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import edu.szu.moments.posts.AdminAuditPort;
import edu.szu.moments.posts.IdentityPort;
import edu.szu.moments.posts.MediaPort;
import edu.szu.moments.posts.PostFailure;
import edu.szu.moments.posts.PostModels.Actor;
import edu.szu.moments.posts.PostModels.MediaCandidate;
import edu.szu.moments.posts.PostModels.MediaView;
import edu.szu.moments.posts.PostModels.Role;

@TestConfiguration(proxyBeanMethods = false)
public class InteractionTestConfiguration {
    @Bean
    InteractionTestPorts interactionTestPorts(JdbcTemplate jdbc) {
        return new InteractionTestPorts(jdbc);
    }

    public static final class InteractionTestPorts implements IdentityPort, MediaPort, AdminAuditPort {
        private final JdbcTemplate jdbc;
        private final ThreadLocal<Actor> currentActor = new ThreadLocal<>();

        InteractionTestPorts(JdbcTemplate jdbc) {
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
            validate(actor, false);
            return actor;
        }

        @Override
        public void lockActiveActor(Actor actor) {
            validate(actor, true);
        }

        private void validate(Actor actor, boolean lock) {
            var rows = jdbc.queryForList("SELECT role, status FROM users WHERE id = ?" + (lock ? " FOR UPDATE" : ""),
                actor.id());
            if (rows.isEmpty() || !"ACTIVE".equals(rows.getFirst().get("status"))
                    || !actor.role().name().equals(rows.getFirst().get("role"))) {
                throw PostFailure.unauthenticated();
            }
        }

        @Override
        public List<MediaCandidate> lockAndInspect(List<Long> mediaIds) {
            return List.of();
        }

        @Override
        public void replaceBindings(long actorId, long postId, List<Long> mediaIds, Instant now) {}

        @Override
        public Map<Long, List<MediaView>> findByPostIds(Set<Long> postIds) {
            return postIds.stream().collect(java.util.stream.Collectors.toMap(id -> id, id -> List.of()));
        }

        @Override
        public void recordDeletePost(long adminId, long postId, Instant occurredAt) {}
    }
}
