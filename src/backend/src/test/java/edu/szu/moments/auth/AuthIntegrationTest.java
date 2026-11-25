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
                "moments.auth.max-concurrent=4", "moments.auth.me-max-concurrent=64"})
class AuthIntegrationTest {
    private static final String API = "/api/v1";
    private static final String PASSWORD = "M01-example-01";

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;
    @Autowired JwtEncoder encoder;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthAdmission admission;

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
