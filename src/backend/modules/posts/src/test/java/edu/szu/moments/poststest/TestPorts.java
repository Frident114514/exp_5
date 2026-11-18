package edu.szu.moments.poststest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import org.springframework.jdbc.core.JdbcTemplate;

import edu.szu.moments.posts.*;
import static edu.szu.moments.posts.PostModels.*;

/** 仅用于动态模块的契约与数据库事务测试，不能作为实际认证或媒体服务部署。 */
public class TestPorts implements IdentityPort, MediaPort, RatingPort, AdminAuditPort {
    private final JdbcTemplate jdbc;
    private final ThreadLocal<Actor> actor = new ThreadLocal<>();
    public volatile boolean failBinding;
    public volatile boolean failAudit;
    public volatile boolean missingRatings;
    public volatile CyclicBarrier actorReadBarrier;
    public final AtomicInteger mediaReads = new AtomicInteger();
    public final AtomicInteger ratingReads = new AtomicInteger();

    public TestPorts(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void as(long id, Role role) { actor.set(new Actor(id, role)); }
    public void logout() { actor.remove(); }
    public void reset() {
        logout();
        failBinding = false;
        failAudit = false;
        missingRatings = false;
        actorReadBarrier = null;
        mediaReads.set(0);
        ratingReads.set(0);
    }

    @Override
    public Actor requireActor() {
        Actor current = actor.get();
        if (current == null) { throw PostFailure.unauthenticated(); }
        validateActor(current, false);
        // 并发回归用：保证两个事务均已建立快照后，再同时执行标签 upsert。
        if (actorReadBarrier != null) {
            try {
                actorReadBarrier.await(5, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new IllegalStateException("并发测试屏障失败", ex);
            }
        }
        return current;
    }

    @Override
    public void lockActiveActor(Actor current) { validateActor(current, true); }

    private void validateActor(Actor current, boolean lock) {
        var rows = jdbc.queryForList("SELECT role,status FROM users WHERE id=?" + (lock ? " FOR UPDATE" : ""), current.id());
        if (rows.isEmpty() || !rows.getFirst().get("status").equals("ACTIVE")
                || !rows.getFirst().get("role").equals(current.role().name())) {
            throw PostFailure.unauthenticated();
        }
    }

    @Override
    public List<MediaCandidate> lockAndInspect(List<Long> ids) {
        if (ids.isEmpty()) { return List.of(); }
        return jdbc.query("SELECT * FROM fixture_media WHERE id IN (" + placeholders(ids.size()) + ") ORDER BY id FOR UPDATE",
            (rs, row) -> new MediaCandidate(rs.getLong("id"), rs.getLong("uploaded_by"),
                rs.getObject("post_id", Long.class), MediaType.valueOf(rs.getString("type")),
                MediaStatus.valueOf(rs.getString("status"))), ids.toArray());
    }

    @Override
    public void replaceBindings(long actorId, long postId, List<Long> ids, Instant now) {
        jdbc.update("UPDATE fixture_media SET post_id=NULL,status='PENDING',sort_order=0 WHERE post_id=?", postId);
        for (int i = 0; i < ids.size(); i++) {
            jdbc.update("UPDATE fixture_media SET post_id=?,status='ACTIVE',sort_order=? WHERE id=? AND uploaded_by=?",
                postId, i, ids.get(i), actorId);
        }
        if (failBinding) { throw PostFailure.conflict("测试夹具：绑定失败"); }
    }

    @Override
    public Map<Long, List<MediaView>> findByPostIds(Set<Long> ids) {
        mediaReads.incrementAndGet();
        Map<Long, List<MediaView>> result = new HashMap<>();
        ids.forEach(id -> result.put(id, new ArrayList<>()));
        if (!ids.isEmpty()) {
            jdbc.query("SELECT * FROM fixture_media WHERE post_id IN (" + placeholders(ids.size()) + ") ORDER BY sort_order",
                rs -> {
                    long id = rs.getLong("id");
                    result.get(rs.getLong("post_id")).add(new MediaView(id, MediaType.valueOf(rs.getString("type")),
                        "/api/v1/media/" + id + "/content"));
                }, ids.toArray());
        }
        return result;
    }

    @Override
    public Map<Long, RatingSummary> summarize(Set<Long> ids) {
        ratingReads.incrementAndGet();
        if (missingRatings) { return Map.of(); }
        Map<Long, RatingSummary> result = new HashMap<>();
        ids.forEach(id -> result.put(id, RatingSummary.empty()));
        if (!ids.isEmpty()) {
            jdbc.query("SELECT post_id,AVG(score) average,COUNT(*) amount FROM fixture_ratings WHERE post_id IN ("
                + placeholders(ids.size()) + ") GROUP BY post_id", rs -> {
                    BigDecimal avg = rs.getBigDecimal("average").setScale(1, RoundingMode.HALF_UP);
                    result.put(rs.getLong("post_id"), new RatingSummary(avg, rs.getLong("amount")));
                }, ids.toArray());
        }
        return result;
    }

    @Override
    public void recordDeletePost(long adminId, long postId, Instant at) {
        jdbc.update("INSERT INTO fixture_audit(admin_id,post_id,created_at) VALUES (?,?,?)",
            adminId, postId, Timestamp.from(at));
        if (failAudit) { throw PostFailure.conflict("测试夹具：管理日志失败"); }
    }

    private String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
