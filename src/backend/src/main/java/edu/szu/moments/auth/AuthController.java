package edu.szu.moments.auth;

import edu.szu.moments.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1")
class AuthController {
    record Registration(String account, String password, String nickname) {}
    record Credentials(String account, String password) {}
    record LoginResult(String accessToken, Instant expiresAt, UserProfile user) {}

    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<UserProfile> register(@RequestBody Registration request) {
        return ApiResponse.ok(auth.register(request));
    }

    @PostMapping("/auth/login")
    ApiResponse<LoginResult> login(@RequestBody Credentials request) {
        return ApiResponse.ok(auth.login(request));
    }

    @GetMapping("/users/me")
    ApiResponse<UserProfile> me(@AuthenticationPrincipal UserProfile user) {
        return ApiResponse.ok(user);
    }
}
