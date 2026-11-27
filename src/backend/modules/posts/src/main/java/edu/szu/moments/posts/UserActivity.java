package edu.szu.moments.posts;

/** 成功业务事务发布；M01 同步记录，回滚时不产生虚假活跃量。 */
public record UserActivity(long userId, java.time.Instant occurredAt) {
    public UserActivity(long userId) { this(userId, java.time.Instant.now()); }
}
