package edu.szu.moments.auth;

import edu.szu.moments.posts.UserActivity;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.ZoneId;

@Component
class ActivityRecorder {
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate db;
    ActivityRecorder(JdbcTemplate db) { this.db = db; }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UserActivity event) {
        // One row per user/day; never store tokens, content or a per-request log.
        var utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        db.update(connection -> {
            var ps = connection.prepareStatement("INSERT INTO daily_user_activity(activity_day,user_id,first_active_at,last_active_at) "
                    + "SELECT ?,id,?,? FROM users WHERE id=? AND status='ACTIVE' AND role='USER' "
                    + "ON DUPLICATE KEY UPDATE first_active_at=LEAST(first_active_at,?),last_active_at=GREATEST(last_active_at,?)");
            var time = Timestamp.from(event.occurredAt());
            ps.setDate(1,Date.valueOf(event.occurredAt().atZone(ZONE).toLocalDate()));
            ps.setTimestamp(2,time,utc); ps.setTimestamp(3,time,utc); ps.setLong(4,event.userId());
            ps.setTimestamp(5,time,utc); ps.setTimestamp(6,time,utc);
            return ps;
        });
    }
}
