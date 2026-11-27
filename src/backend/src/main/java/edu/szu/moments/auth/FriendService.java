package edu.szu.moments.auth;

import edu.szu.moments.api.ApiFailure;
import edu.szu.moments.api.PageData;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
class FriendService {
    record SearchUser(long id, String account, String nickname, String relationship, Long requestId) {}
    record RequestRow(long id, UserProfile requester, UserProfile recipient, String status,
                      Instant createdAt, Instant handledAt) {}
    private final JdbcTemplate db;
    private final UserRepository users;

    FriendService(JdbcTemplate db, UserRepository users) { this.db = db; this.users = users; }

    static void page(int page, int size) {
        if (page < 1 || size < 1 || size > 50 || (long)(page-1)*size > Integer.MAX_VALUE)
            throw invalid("页码须大于 0，每页 1–50 条");
    }

    PageData<SearchUser> search(UserProfile actor, String keyword, int page, int size) {
        ordinary(actor); page(page,size);
        String q = keyword == null ? "" : keyword.strip();
        if (q.isEmpty() || q.codePointCount(0,q.length()) > 64) throw invalid("关键词须为 1–64 个字符");
        String where = " FROM users u WHERE u.id<>? AND u.role='USER' AND u.status='ACTIVE' "
                + "AND (LOCATE(LOWER(?),LOWER(u.account))>0 OR LOCATE(LOWER(?),LOWER(u.nickname))>0)";
        long total = db.queryForObject("SELECT COUNT(*)"+where, Long.class, actor.id(),q,q);
        // Correlated indexed lookups retain correct pagination and never load every friendship into memory.
        String projection = "SELECT u.id,u.account,u.nickname,"
                + "EXISTS(SELECT 1 FROM friendships f WHERE f.user_low=LEAST(u.id,?) AND f.user_high=GREATEST(u.id,?)) is_friend,"
                + "(SELECT r.requester_id FROM friend_requests r WHERE r.user_low=LEAST(u.id,?) AND r.user_high=GREATEST(u.id,?) AND r.status='PENDING') sender,"
                + "(SELECT r.id FROM friend_requests r WHERE r.user_low=LEAST(u.id,?) AND r.user_high=GREATEST(u.id,?) AND r.status='PENDING') request_id";
        var rows = db.query(projection+where+" ORDER BY u.id DESC LIMIT ? OFFSET ?", (rs,n) -> {
            Long sender = rs.getObject("sender",Long.class);
            String relation = rs.getBoolean("is_friend") ? "FRIEND" : sender == null ? "NONE"
                    : sender.equals(actor.id()) ? "PENDING_SENT" : "PENDING_RECEIVED";
            return new SearchUser(rs.getLong("id"),rs.getString("account"),rs.getString("nickname"),
                    relation,rs.getObject("request_id",Long.class));
        }, actor.id(),actor.id(),actor.id(),actor.id(),actor.id(),actor.id(),actor.id(),q,q,size,(page-1)*size);
        return new PageData<>(rows,page,size,total);
    }

    @Transactional
    public RequestRow send(UserProfile actor, int version, long target) {
        lockPair(actor,version,target,true);
        long low = Math.min(actor.id(),target), high = Math.max(actor.id(),target);
        if (db.queryForObject("SELECT COUNT(*) FROM friendships WHERE user_low=? AND user_high=?",Long.class,low,high)>0
                || db.queryForObject("SELECT COUNT(*) FROM friend_requests WHERE user_low=? AND user_high=? AND status='PENDING'",Long.class,low,high)>0)
            throw conflict("已经是好友或已有待处理申请");
        var key = new GeneratedKeyHolder();
        db.update(connection -> {
            var ps = connection.prepareStatement("INSERT INTO friend_requests(user_low,user_high,requester_id,recipient_id,status,created_at) "
                    + "VALUES (?,?,?,?,'PENDING',UTC_TIMESTAMP(6))", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1,low); ps.setLong(2,high); ps.setLong(3,actor.id()); ps.setLong(4,target);
            return ps;
        },key);
        return request(key.getKey().longValue());
    }

    PageData<RequestRow> requests(UserProfile actor, String direction, int page, int size) {
        ordinary(actor); page(page,size);
        if (!List.of("incoming","outgoing").contains(direction)) throw invalid("direction 须为 incoming 或 outgoing");
        String column = direction.equals("incoming") ? "recipient_id" : "requester_id";
        String where = " WHERE r."+column+"=? AND r.status='PENDING' AND a.status='ACTIVE' AND b.status='ACTIVE'";
        long total = db.queryForObject("SELECT COUNT(*)"+REQUEST_FROM+where,Long.class,actor.id());
        return new PageData<>(db.query(REQUEST_SELECT+REQUEST_FROM+where+" ORDER BY r.id DESC LIMIT ? OFFSET ?",
                REQUEST_MAPPER,actor.id(),size,(page-1)*size),page,size,total);
    }

    @Transactional
    public RequestRow decide(UserProfile actor, int version, long id, String action) {
        ordinary(actor);
        if (!"ACCEPT".equals(action) && !"REJECT".equals(action)) throw invalid("action 须为 ACCEPT 或 REJECT");
        var original = request(id);
        if (!original.recipient().id().equals(actor.id())) throw forbidden();
        lockPair(actor,version,original.requester().id(),true);
        var current = db.query(REQUEST_SELECT+REQUEST_FROM+" WHERE r.id=? FOR UPDATE",REQUEST_MAPPER,id).getFirst();
        if (!current.status().equals("PENDING")) throw conflict("申请已处理或取消");
        if (action.equals("ACCEPT")) db.update("INSERT INTO friendships(user_low,user_high,created_at) VALUES (?,?,UTC_TIMESTAMP(6))",
                Math.min(actor.id(),current.requester().id()),Math.max(actor.id(),current.requester().id()));
        db.update("UPDATE friend_requests SET status=?,handled_at=UTC_TIMESTAMP(6) WHERE id=?",
                action.equals("ACCEPT") ? "ACCEPTED" : "REJECTED",id);
        return request(id);
    }

    PageData<UserProfile> friends(UserProfile actor, int page, int size) {
        ordinary(actor); page(page,size);
        String where = " FROM friendships f JOIN users u ON u.id=CASE WHEN f.user_low=? THEN f.user_high ELSE f.user_low END "
                + "WHERE (f.user_low=? OR f.user_high=?) AND u.status='ACTIVE' AND u.role='USER'";
        long total = db.queryForObject("SELECT COUNT(*)"+where,Long.class,actor.id(),actor.id(),actor.id());
        var rows = db.query("SELECT u.id,u.account,u.nickname,u.role"+where+" ORDER BY u.id DESC LIMIT ? OFFSET ?",
                (rs,n)->new UserProfile(rs.getLong("id"),rs.getString("account"),rs.getString("nickname"),rs.getString("role")),
                actor.id(),actor.id(),actor.id(),size,(page-1)*size);
        return new PageData<>(rows,page,size,total);
    }

    @Transactional
    public void remove(UserProfile actor, int version, long target) {
        lockPair(actor,version,target,false);
        db.update("DELETE FROM friendships WHERE user_low=? AND user_high=?",Math.min(actor.id(),target),Math.max(actor.id(),target));
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void disabled(UserDisabled event) {
        db.update("DELETE FROM friendships WHERE user_low=? OR user_high=?",event.userId(),event.userId());
        db.update("UPDATE friend_requests SET status='CANCELLED',handled_at=UTC_TIMESTAMP(6) "
                + "WHERE status='PENDING' AND (requester_id=? OR recipient_id=?)",event.userId(),event.userId());
    }

    private void lockPair(UserProfile actor, int version, long target, boolean activeTarget) {
        ordinary(actor);
        if (target < 1 || target == actor.id()) throw invalid("目标须为其他用户的正整数 ID");
        var first = users.lockById(Math.min(actor.id(),target)).orElseThrow(FriendService::missing);
        var second = users.lockById(Math.max(actor.id(),target)).orElseThrow(FriendService::missing);
        var caller = first.id().equals(actor.id()) ? first : second;
        var other = first.id().equals(actor.id()) ? second : first;
        if (!caller.active() || caller.tokenVersion()!=version) throw new ApiFailure(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","请重新登录");
        if (caller.role()!=UserAccount.Role.USER) throw forbidden();
        if (other.role()!=UserAccount.Role.USER || activeTarget && !other.active()) throw missing();
    }

    private RequestRow request(long id) {
        if (id<1) throw invalid("申请 ID 须为正整数");
        return db.query(REQUEST_SELECT+REQUEST_FROM+" WHERE r.id=?",REQUEST_MAPPER,id).stream().findFirst().orElseThrow(FriendService::missing);
    }
    private static final String REQUEST_SELECT = "SELECT r.*,a.account aa,a.nickname an,b.account ba,b.nickname bn";
    private static final String REQUEST_FROM = " FROM friend_requests r JOIN users a ON a.id=r.requester_id JOIN users b ON b.id=r.recipient_id";
    private static final RowMapper<RequestRow> REQUEST_MAPPER = (rs,n) -> new RequestRow(rs.getLong("id"),
            new UserProfile(rs.getLong("requester_id"),rs.getString("aa"),rs.getString("an"),"USER"),
            new UserProfile(rs.getLong("recipient_id"),rs.getString("ba"),rs.getString("bn"),"USER"),
            rs.getString("status"),rs.getTimestamp("created_at",java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))).toInstant(),
            rs.getTimestamp("handled_at",java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")))==null ? null
                    : rs.getTimestamp("handled_at",java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))).toInstant());
    private static void ordinary(UserProfile actor) { if (!"USER".equals(actor.role())) throw forbidden(); }
    private static ApiFailure invalid(String text) { return new ApiFailure(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR",text); }
    private static ApiFailure missing() { return new ApiFailure(HttpStatus.NOT_FOUND,"NOT_FOUND","用户或申请不存在"); }
    private static ApiFailure conflict(String text) { return new ApiFailure(HttpStatus.CONFLICT,"CONFLICT",text); }
    private static ApiFailure forbidden() { return new ApiFailure(HttpStatus.FORBIDDEN,"FORBIDDEN","无权执行此好友操作"); }
}
