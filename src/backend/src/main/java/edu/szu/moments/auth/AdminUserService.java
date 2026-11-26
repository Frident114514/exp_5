package edu.szu.moments.auth;

import edu.szu.moments.api.ApiFailure;
import edu.szu.moments.api.PageData;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
class AdminUserService {
    record UserRow(Long id, String account, String nickname, String role, String status, Instant createdAt) {
        static UserRow of(UserAccount user) {
            return new UserRow(user.id(), user.account(), user.nickname(), user.role().name(),
                    user.status().name(), user.createdAt());
        }
    }

    private final UserRepository users;
    private final JdbcTemplate db;
    private final ApplicationEventPublisher events;

    AdminUserService(UserRepository users, JdbcTemplate db, ApplicationEventPublisher events) {
        this.users = users;
        this.db = db;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageData<UserRow> list(String keyword, int page, int pageSize) {
        String query = keyword == null ? "" : keyword.strip();
        if (page < 1 || pageSize < 1 || pageSize > 50 || (long) (page - 1) * pageSize > Integer.MAX_VALUE
                || query.codePointCount(0, query.length()) > 64) {
            throw new ApiFailure(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "页码须大于 0，每页 1–50 条，关键词最多 64 个字符");
        }
        var result = users.search(query, PageRequest.of(page - 1, pageSize, Sort.by("id").descending()));
        return new PageData<>(result.getContent().stream().map(UserRow::of).toList(), page, pageSize, result.getTotalElements());
    }

    @Transactional
    public void disable(long adminId, int expectedVersion, long targetId) {
        if (targetId < 1) throw new ApiFailure(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "用户 ID 须大于 0");
        if (adminId == targetId) throw forbidden();
        // All two-user operations use ascending IDs, avoiding reciprocal row-lock deadlocks.
        var first = users.lockById(Math.min(adminId, targetId)).orElseThrow(AdminUserService::notFound);
        var second = users.lockById(Math.max(adminId, targetId)).orElseThrow(AdminUserService::notFound);
        var admin = first.id() == adminId ? first : second;
        var target = first.id() == targetId ? first : second;
        if (!admin.active() || admin.tokenVersion() != expectedVersion) {
            throw new ApiFailure(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请登录或重新登录");
        }
        if (admin.role() != UserAccount.Role.ADMIN || target.role() == UserAccount.Role.ADMIN) throw forbidden();
        if (!target.active()) return;
        target.disable();
        events.publishEvent(new UserDisabled(targetId));
        db.update("INSERT INTO admin_operation_logs(admin_id,action,target_type,target_id,created_at) "
                + "VALUES (?,'DISABLE_USER','USER',?,UTC_TIMESTAMP(6))", adminId, targetId);
    }

    private static ApiFailure forbidden() {
        return new ApiFailure(HttpStatus.FORBIDDEN, "FORBIDDEN", "不能删除本人或管理员账号");
    }

    private static ApiFailure notFound() {
        return new ApiFailure(HttpStatus.NOT_FOUND, "NOT_FOUND", "用户不存在");
    }
}
