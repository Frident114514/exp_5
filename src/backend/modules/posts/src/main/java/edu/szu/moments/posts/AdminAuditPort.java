package edu.szu.moments.posts;

import java.time.Instant;

/** M01 实现；日志与删除共用当前事务，日志失败应使删除回滚。 */
public interface AdminAuditPort {
    void recordDeletePost(long adminId, long postId, Instant occurredAt);
}

