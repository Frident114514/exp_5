package edu.szu.moments.auth;

import edu.szu.moments.posts.AdminAuditPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
class PostAdminAuditAdapter implements AdminAuditPort {
    private final JdbcTemplate db;

    PostAdminAuditAdapter(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDeletePost(long adminId, long postId, Instant occurredAt) {
        db.update("INSERT INTO admin_operation_logs(admin_id,action,target_type,target_id,created_at) VALUES (?,?,?,?,?)",
                adminId, "DELETE_POST", "POST", postId, java.sql.Timestamp.from(occurredAt));
    }
}
