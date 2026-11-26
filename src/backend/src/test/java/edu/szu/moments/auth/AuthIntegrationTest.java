package edu.szu.moments.auth;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"moments.auth.requests-per-second=1000", "moments.auth.burst=1000",
                "moments.auth.max-concurrent=4", "moments.auth.me-max-concurrent=64", "moments.diagnostics.enabled=true"})
class AuthIntegrationTest {
    private static final String API = "/api/v1";
    private static final String PASSWORD = "M01-example-01";

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;
    @Autowired JwtEncoder encoder;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthAdmission admission;
    @Autowired AuthService authService;
    @Autowired AdminUserService adminUsers;
    @Autowired org.springframework.context.ConfigurableApplicationContext context;

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "MOMENTS_ADMIN_ACCOUNT", matches = "verify_admin")
    void startupAdminUsesEnvironmentWithoutExposingCredentials() {
        var admin = db.queryForMap("SELECT role,nickname FROM users WHERE account='verify_admin'");
        assertThat(admin.get("role")).isEqualTo("ADMIN");
        assertThat(admin.get("nickname")).isEqualTo("Verification admin");
    }

    @Test
    void concurrentPasswordChangesAllowOnlyOneVersionAndRejectStaleAdminMutation() throws Exception {
        String account = account(); long id = register(account).getBody().path("data").path("id").asLong();
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (String password : new String[]{"New-password-01", "New-password-02"}) {
                tasks.add(executor.submit(() -> {
                    ready.countDown(); start.await();
                    try {
                        authService.changePassword(id, 0, new AuthController.PasswordChange(PASSWORD, password));
                        return 200;
                    } catch (edu.szu.moments.api.ApiFailure failure) { return failure.status().value(); }
                }));
            }
            try { assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
            finally { start.countDown(); }
            assertThat(java.util.List.of(tasks.get(0).get(), tasks.get(1).get())).containsExactlyInAnyOrder(200, 401);
        }
        assertThat(db.queryForObject("SELECT token_version FROM users WHERE id=?", Integer.class, id)).isEqualTo(1);
        String admin = account(); authService.initializeAdmin(admin, PASSWORD, "管理员");
        long adminId = get("/users/me", token(admin)).getBody().path("data").path("id").asLong();
        db.update("UPDATE users SET token_version=1 WHERE id=?", adminId);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adminUsers.disable(adminId, 0, id))
                .isInstanceOfSatisfying(edu.szu.moments.api.ApiFailure.class,
                        failure -> assertThat(failure.status().value()).isEqualTo(401));
        assertThat(db.queryForObject("SELECT status FROM users WHERE id=?", String.class, id)).isEqualTo("ACTIVE");
    }

    @Test
    void passwordChangeRejectsInvalidInputAndRevokesAllOldTokens() {
        String account = account();
        long id = register(account).getBody().path("data").path("id").asLong();
        String oldToken = token(account);
        expect(change(oldToken, PASSWORD, "short"), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expect(change(oldToken, "wrong-password", "New-password-01"), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        expect(change(oldToken, PASSWORD, PASSWORD), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expect(get("/users/me", oldToken), HttpStatus.OK, "OK");
        expect(change(oldToken, PASSWORD, "New-password-01"), HttpStatus.OK, "OK");
        expect(get("/users/me", oldToken), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        expect(change(oldToken, PASSWORD, "Another-password"), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        expect(login(account, PASSWORD), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        expect(login(account, "New-password-01"), HttpStatus.OK, "OK");
        assertThat(db.queryForObject("SELECT token_version FROM users WHERE id=?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void passwordChangeUsesPasswordAdmissionAndRequiresLogin() throws Exception {
        String account = account(); register(account); String token = token(account);
        expect(change(null, PASSWORD, "New-password-01"), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        var permits = new java.util.ArrayList<AutoCloseable>();
        try {
            for (int i=0; i<4; i++) permits.add(admission.admit(true));
            expect(change(token, PASSWORD, "New-password-01"), HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED");
        } finally { for (var permit : permits) permit.close(); }
        expect(change(token, PASSWORD, "New-password-01"), HttpStatus.OK, "OK");
    }

    @Test
    void adminInitializationIsIdempotentAndNeverPromotesExistingUser() {
        String account = account();
        authService.initializeAdmin(account, PASSWORD, "管理测试");
        authService.initializeAdmin(account, "Different-password", "不覆盖");
        var login = login(account, PASSWORD);
        expect(login, HttpStatus.OK, "OK");
        assertThat(login.getBody().path("data").path("user").path("role").asText()).isEqualTo("ADMIN");
        String ordinary = account(); register(ordinary);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> authService.initializeAdmin(ordinary, PASSWORD, "管理测试"))
                .isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> authService.initializeAdmin(account(), null, "管理测试"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void adminListsLiteralKeywordsWithPaginationAndNoSecrets() {
        String admin = account(); authService.initializeAdmin(admin, PASSWORD, "管理员");
        String token = token(admin); String prefix = account();
        register(prefix + "a"); register(prefix + "b");
        var result = get("/admin/users?keyword="+prefix+"&page=1&pageSize=1", token);
        expect(result, HttpStatus.OK, "OK");
        var data = result.getBody().path("data");
        assertThat(data.path("total").asInt()).isEqualTo(2);
        assertThat(data.path("items").size()).isEqualTo(1);
        assertThat(data.path("items").get(0).path("account").asText()).isEqualTo(prefix+"b");
        assertThat(data.path("items").get(0).path("status").asText()).isEqualTo("ACTIVE");
        assertThat(data.path("items").get(0).hasNonNull("createdAt")).isTrue();
        assertThat(result.getBody().toString()).doesNotContain("password", "tokenVersion", "token_version");
        assertThat(get("/admin/users?keyword="+prefix+"&page=2&pageSize=1", token).getBody()
                .path("data").path("items").get(0).path("account").asText()).isEqualTo(prefix+"a");
        assertThat(get("/admin/users?keyword=%25", token).getBody().path("data").path("total").asInt()).isZero();
        for (String query : new String[]{"page=0", "pageSize=51", "pageSize=0", "page=abc",
                "page=2147483647&pageSize=50", "keyword="+"x".repeat(65)}) {
            expect(get("/admin/users?"+query, token), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        }
    }

    @Test
    void adminDisableIsAtomicAuditedIdempotentAndRevokesTokens() {
        String admin = account(); authService.initializeAdmin(admin, PASSWORD, "管理员");
        String adminToken = token(admin);
        long adminId = get("/users/me", adminToken).getBody().path("data").path("id").asLong();
        String account = account(); long id = register(account).getBody().path("data").path("id").asLong();
        String token = token(account);
        expect(delete(id, token), HttpStatus.FORBIDDEN, "FORBIDDEN");
        expect(delete(id, null), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        expect(delete(adminId, adminToken), HttpStatus.FORBIDDEN, "FORBIDDEN");
        String other = account(); authService.initializeAdmin(other, PASSWORD, "另一个管理员");
        long otherId = get("/users/me", token(other)).getBody().path("data").path("id").asLong();
        expect(delete(otherId, adminToken), HttpStatus.FORBIDDEN, "FORBIDDEN");
        expect(delete(Long.MAX_VALUE, adminToken), HttpStatus.NOT_FOUND, "NOT_FOUND");
        expect(delete(id, adminToken), HttpStatus.OK, "OK");
        expect(delete(id, adminToken), HttpStatus.OK, "OK");
        expect(get("/users/me", token), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        expect(login(account, PASSWORD), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertThat(db.queryForObject("SELECT token_version FROM users WHERE id=?", Integer.class, id)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM admin_operation_logs WHERE target_id=? AND admin_id=?",
                Integer.class, id, adminId)).isEqualTo(1);
        assertThat(get("/admin/users?keyword="+account, adminToken).getBody().path("data").path("items")
                .get(0).path("status").asText()).isEqualTo("DISABLED");
    }

    @Test
    void synchronousDisableFailureRollsBackUserAndAudit() {
        String admin = account(); authService.initializeAdmin(admin, PASSWORD, "管理员");
        long adminId = get("/users/me", token(admin)).getBody().path("data").path("id").asLong();
        String account = account(); long id = register(account).getBody().path("data").path("id").asLong();
        var multicaster = context.getBean("applicationEventMulticaster",
                org.springframework.context.event.ApplicationEventMulticaster.class);
        org.springframework.context.ApplicationListener<org.springframework.context.PayloadApplicationEvent<?>> listener = event -> {
            if (event.getPayload() instanceof UserDisabled disabled && disabled.userId() == id) {
                db.update("UPDATE users SET nickname='listener update' WHERE id=?", id);
                throw new IllegalStateException("simulated module failure");
            }
        };
        multicaster.addApplicationListener(listener);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> adminUsers.disable(adminId, 0, id))
                    .isInstanceOf(IllegalStateException.class);
        } finally { multicaster.removeApplicationListener(listener); }
        assertThat(db.queryForObject("SELECT status FROM users WHERE id=?", String.class, id)).isEqualTo("ACTIVE");
        assertThat(db.queryForObject("SELECT token_version FROM users WHERE id=?", Integer.class, id)).isZero();
        assertThat(db.queryForObject("SELECT nickname FROM users WHERE id=?", String.class, id)).isEqualTo("测试用户");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM admin_operation_logs WHERE target_id=?", Integer.class, id)).isZero();
    }

    private ResponseEntity<JsonNode> change(String token, String oldPassword, String newPassword) {
        return authenticated("/users/me/password", HttpMethod.PUT, token,
                Map.of("oldPassword", oldPassword, "newPassword", newPassword));
    }

    private ResponseEntity<JsonNode> delete(long id, String token) {
        return authenticated("/admin/users/"+id, HttpMethod.DELETE, token, null);
    }

    private ResponseEntity<JsonNode> authenticated(String path, HttpMethod method, String token, Object body) {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(API+path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    @Test
    void diagnosticsRequireAdminAndCorrelateActualHttpRequests() {
        String account = account();
        long id = register(account).getBody().path("data").path("id").asLong();
        String token = token(account);
        expect(get("/internal/m01-diagnostics?trial=http-observation", token), HttpStatus.FORBIDDEN, "FORBIDDEN");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);headers.set("X-M01-Trial", "http-observation");headers.set("X-M01-Request-Id", "0");
        expect(http.exchange(API + "/users/me", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class), HttpStatus.OK, "OK");
        db.update("UPDATE users SET role='ADMIN' WHERE id=?", id);
        var observed = get("/internal/m01-diagnostics?trial=http-observation", token);
        assertThat(observed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(observed.getBody().path("entered").asInt()).isEqualTo(1);
        var trace = observed.getBody().path("traces").get(0);
        assertThat(trace.path("id").asInt()).isZero();
        assertThat(trace.path("databaseEndNs").asLong()).isGreaterThanOrEqualTo(trace.path("databaseStartNs").asLong());
        assertThat(trace.path("databaseStartNs").asLong()).isGreaterThanOrEqualTo(trace.path("admittedNs").asLong());
        assertThat(observed.getBody().toString()).doesNotContain(token, account, "password");
    }

    @Test
    void httpRejectsOverloadAndRecoversWithoutAffectingOtherIdentityTraffic() throws Exception {
        var passwordPermits = new java.util.ArrayList<AutoCloseable>();
        try {
            for (int i = 0; i < 4; i++) passwordPermits.add(admission.admit(true));
            var busy = login("unknown_user", PASSWORD);
            expect(busy, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED");
            assertThat(busy.getHeaders().getFirst("Retry-After")).isEqualTo("1");
            expect(get("/users/me", null), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        } finally {
            for (var permit : passwordPermits) permit.close();
        }
        expect(login("unknown_user", PASSWORD), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");

        var identityPermits = new java.util.ArrayList<AutoCloseable>();
        try {
            for (int i = 0; i < 64; i++) identityPermits.add(admission.admit(false));
            expect(get("/users/me", null), HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_BUSY");
        } finally {
            for (var permit : identityPermits) permit.close();
        }
        expect(get("/users/me", null), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }

    @Test
    void flywayMigratesMysql84AndCreatesUsers() {
        assertThat(db.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.");
        assertThat(db.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void registrationForcesUserRoleAndOnlyStoresBcryptHash() {
        String account = account();
        var result = register(account.toUpperCase(Locale.ROOT));
        expect(result, HttpStatus.CREATED, "OK");
        JsonNode user = result.getBody().path("data");
        assertThat(user.path("id").asLong()).isPositive();
        assertThat(user.path("account").asText()).isEqualTo(account);
        assertThat(user.path("nickname").asText()).isEqualTo("测试用户");
        assertThat(user.path("role").asText()).isEqualTo("USER");
        assertThat(user.has("password")).isFalse();
        assertThat(user.has("passwordHash")).isFalse();
        String stored = db.queryForObject("SELECT password_hash FROM users WHERE account = ?", String.class, account);
        assertThat(stored).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(passwords.matches(PASSWORD, stored)).isTrue();
    }

    @Test
    void duplicateAccountReturnsConflictAndLeavesOneRow() {
        String account = account();
        expect(register(account), HttpStatus.CREATED, "OK");
        expect(register(" " + account.toUpperCase(Locale.ROOT) + " "), HttpStatus.CONFLICT, "CONFLICT");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM users WHERE account = ?", Integer.class, account))
                .isEqualTo(1);
    }

    @Test
    void rejectsInvalidRegistrationAndMalformedJson() {
        expect(post("/auth/register", Map.of("account", "A!", "password", PASSWORD, "nickname", "测试")),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expect(post("/auth/register", Map.of("account", account(), "password", "汉".repeat(25), "nickname", "测试")),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expect(post("/auth/register", Map.of("account", account(), "password", PASSWORD, "nickname", "  ")),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        expect(post("/auth/register", "{"), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    @Test
    void loginIssuesTwelveHourTokenAndMeReturnsSameUser() {
        String account = account();
        var registered = register(account).getBody().path("data");
        var result = login(account, PASSWORD);
        expect(result, HttpStatus.OK, "OK");
        JsonNode data = result.getBody().path("data");
        assertThat(data.path("user")).isEqualTo(registered);
        assertThat(Duration.between(Instant.now(), Instant.parse(data.path("expiresAt").asText())))
                .isBetween(Duration.ofHours(12).minusSeconds(10), Duration.ofHours(12));
        var me = get("/users/me", data.path("accessToken").asText());
        expect(me, HttpStatus.OK, "OK");
        assertThat(me.getBody().path("data")).isEqualTo(registered);
    }

    @Test
    void wrongPasswordAndMissingAccountReturnSameUnauthorizedResponse() {
        String account = account();
        expect(register(account), HttpStatus.CREATED, "OK");
        var wrong = login(account, "wrong-password");
        var unknown = login(account(), PASSWORD);
        expect(wrong, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        expect(unknown, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertThat(wrong.getBody()).isEqualTo(unknown.getBody());
    }

    @Test
    void protectedEndpointRejectsMissingMalformedAndAlteredTokens() {
        String account = account();
        expect(register(account), HttpStatus.CREATED, "OK");
        String token = token(account);
        int signature = token.lastIndexOf('.') + 1;
        String altered = token.substring(0, signature) + (token.charAt(signature) == 'A' ? 'B' : 'A')
                + token.substring(signature + 1);
        expect(get("/users/me", null), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        expect(get("/users/me", "not-a-jwt"), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        expect(get("/users/me", altered), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }

    @Test
    void protectedEndpointRejectsExpiredAndStaleTokensAndDisabledUsers() {
        String account = account();
        long id = register(account).getBody().path("data").path("id").asLong();
        String active = token(account);
        Instant now = Instant.now();
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer(SecurityConfiguration.ISSUER).subject(Long.toString(id))
                        .issuedAt(now.minusSeconds(120)).expiresAt(now.minusSeconds(5)).claim("ver", 0).build()))
                .getTokenValue();
        expect(get("/users/me", expired), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        db.update("UPDATE users SET token_version = 1 WHERE id = ?", id);
        expect(get("/users/me", active), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        String renewed = token(account);
        expect(get("/users/me", renewed), HttpStatus.OK, "OK");
        db.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", id);
        expect(get("/users/me", renewed), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        expect(login(account, PASSWORD), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }

    @Test
    void userCannotPassAdminSecurityGate() {
        String account = account();
        expect(register(account), HttpStatus.CREATED, "OK");
        expect(get("/admin/users", token(account)), HttpStatus.FORBIDDEN, "FORBIDDEN");
    }

    private static String account() {
        return "t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private ResponseEntity<JsonNode> register(String account) {
        return post("/auth/register", Map.of("account", account, "password", PASSWORD, "nickname", " 测试用户 ",
                "role", "ADMIN"));
    }

    private ResponseEntity<JsonNode> login(String account, String password) {
        return post("/auth/login", Map.of("account", account, "password", password));
    }

    private String token(String account) {
        var result = login(account, PASSWORD);
        expect(result, HttpStatus.OK, "OK");
        return result.getBody().path("data").path("accessToken").asText();
    }

    private ResponseEntity<JsonNode> post(String route, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity(API + route, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String route, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(API + route, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    private static void expect(ResponseEntity<JsonNode> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("code").asText()).isEqualTo(code);
        if (status.isError()) assertThat(response.getBody().path("data").isNull()).isTrue();
    }
}
