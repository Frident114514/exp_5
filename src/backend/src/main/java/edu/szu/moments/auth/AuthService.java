package edu.szu.moments.auth;

import edu.szu.moments.api.ApiFailure;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
class AuthService {
    private static final Pattern ACCOUNT = Pattern.compile("[A-Za-z0-9_]{3,32}");
    private static final Duration TOKEN_TTL = Duration.ofHours(12);

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final String missingUserHash;
    private final org.springframework.context.ApplicationEventPublisher events;

    AuthService(UserRepository users, PasswordEncoder passwords, JwtEncoder encoder,
                org.springframework.context.ApplicationEventPublisher events) {
        this.events = events;
        this.users = users;
        this.passwords = passwords;
        this.encoder = encoder;
        this.missingUserHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    UserProfile register(AuthController.Registration request) {
        String account = normalizeAccount(request.account());
        if (account == null) {
            throw invalid("账号须为 3–32 个英文字母、数字或下划线");
        }
        if (!validPassword(request.password())) {
            throw invalid("密码至少 8 个字符，UTF-8 编码最长 72 字节，且不能全为空白");
        }
        String nickname = request.nickname() == null ? "" : request.nickname().strip();
        if (nickname.isEmpty() || nickname.codePointCount(0, nickname.length()) > 32) {
            throw invalid("昵称须为 1–32 个字符");
        }

        var user = new UserAccount(account, passwords.encode(request.password()), nickname);
        try {
            return users.saveAndFlush(user).profile();
        } catch (DataIntegrityViolationException failure) {
            if (failure.getMostSpecificCause() instanceof java.sql.SQLException sql && sql.getErrorCode() == 1062
                    || failure.getCause() instanceof ConstraintViolationException constraint
                    && "uk_users_account".equals(constraint.getConstraintName())) {
                throw new ApiFailure(HttpStatus.CONFLICT, "CONFLICT", "账号已存在");
            }
            throw failure;
        }
    }

    @Transactional
    public AuthController.LoginResult login(AuthController.Credentials request) {
        String account = normalizeAccount(request.account());
        // Invalid credential shapes use the same response as unknown accounts and wrong passwords.
        if (account == null || !validPassword(request.password())) {
            throw badCredentials();
        }
        UserAccount user = users.findByAccount(account).orElse(null);
        boolean matches = passwords.matches(request.password(), user == null ? missingUserHash : user.passwordHash());
        if (!matches || user == null || !user.active()) {
            throw badCredentials();
        }

        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(TOKEN_TTL);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfiguration.ISSUER).subject(user.id().toString())
                .issuedAt(issuedAt).expiresAt(expiresAt)
                .claim("ver", user.tokenVersion()).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        events.publishEvent(new edu.szu.moments.posts.UserActivity(user.id()));
        return new AuthController.LoginResult(token, expiresAt, user.profile());
    }

    @Transactional
    public void changePassword(long id, int expectedVersion, AuthController.PasswordChange request) {
        if (!validPassword(request.newPassword()) || !validPassword(request.oldPassword())) {
            throw invalid("密码至少 8 个字符，UTF-8 编码最长 72 字节，且不能全为空白");
        }
        var user = users.lockById(id).orElseThrow(AuthService::badCredentials);
        if (!user.active() || user.tokenVersion() != expectedVersion
                || !passwords.matches(request.oldPassword(), user.passwordHash())) throw badCredentials();
        if (request.oldPassword().equals(request.newPassword())) throw invalid("新密码不能与原密码相同");
        user.changePassword(passwords.encode(request.newPassword()));
    }

    @Transactional
    public void initializeAdmin(String rawAccount, String password, String rawNickname) {
        String account = normalizeAccount(rawAccount);
        if (account == null || !validPassword(password)) {
            throw new IllegalStateException("Invalid administrator account or password configuration");
        }
        String nickname = rawNickname == null ? "管理员" : rawNickname.strip();
        if (nickname.isEmpty() || nickname.codePointCount(0, nickname.length()) > 32) {
            throw new IllegalStateException("Invalid administrator nickname configuration");
        }
        var existing = users.findByAccount(account);
        if (existing.isPresent()) {
            if (existing.get().role() != UserAccount.Role.ADMIN) {
                throw new IllegalStateException("Administrator account conflicts with an existing user");
            }
            return;
        }
        var user = new UserAccount(account, passwords.encode(password), nickname);
        user.makeAdmin();
        users.saveAndFlush(user);
    }

    private static String normalizeAccount(String raw) {
        if (raw == null) return null;
        String account = raw.strip();
        return ACCOUNT.matcher(account).matches() ? account.toLowerCase(Locale.ROOT) : null;
    }

    private static boolean validPassword(String value) {
        return value != null && value.length() <= 72 && !value.isBlank()
                && value.codePointCount(0, value.length()) >= 8
                && value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    private static ApiFailure invalid(String message) {
        return new ApiFailure(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private static ApiFailure badCredentials() {
        return new ApiFailure(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "账号或密码不正确");
    }
}
