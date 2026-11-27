package edu.szu.moments.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** M02 查询好友动态时可复用；分页筛选应直接使用 friendships 的 EXISTS 条件。 */
@Service
public class FriendRelations {
    private final JdbcTemplate db;

    public FriendRelations(JdbcTemplate db) { this.db = db; }

    @Transactional(readOnly = true)
    public boolean areFriends(long a, long b) {
        if (a < 1 || b < 1 || a == b) return false;
        return db.queryForObject("SELECT COUNT(*) FROM friendships f JOIN users l ON l.id=f.user_low "
                + "JOIN users h ON h.id=f.user_high WHERE f.user_low=? AND f.user_high=? "
                + "AND l.status='ACTIVE' AND h.status='ACTIVE'", Long.class, Math.min(a,b), Math.max(a,b)) > 0;
    }
}
