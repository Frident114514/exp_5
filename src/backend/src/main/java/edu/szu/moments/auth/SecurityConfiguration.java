package edu.szu.moments.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import edu.szu.moments.api.ApiResponse;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {
    static final String ISSUER = "moments-backend";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecretKey jwtKey(@Value("${moments.jwt.secret}") String configuredSecret) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(configuredSecret);
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("MOMENTS_JWT_SECRET must be Base64-encoded");
        }
        if (key.length < 32) {
            throw new IllegalStateException("MOMENTS_JWT_SECRET must contain at least 32 decoded bytes");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtIssuerValidator(ISSUER), new JwtTimestampValidator(Duration.ZERO),
                jwt -> jwt.getExpiresAt() == null
                        ? OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))
                        : OAuth2TokenValidatorResult.success()));
        return decoder;
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, UserRepository users, ObjectMapper mapper,
                                    AuthAdmission admission) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(new AuthLoadFilter(admission, mapper), BearerTokenAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, failure) -> unauthenticated(mapper, response))
                        .accessDeniedHandler((request, response, failure) ->
                                error(mapper, response, 403, "FORBIDDEN", "权限不足")))
                .oauth2ResourceServer(resource -> resource
                        .authenticationEntryPoint((request, response, failure) -> unauthenticated(mapper, response))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> authenticate(token, users))))
                .build();
    }

    private static AbstractAuthenticationToken authenticate(Jwt jwt, UserRepository users) {
        Object version = jwt.getClaims().get("ver");
        if (!(version instanceof Integer || version instanceof Long)) {
            throw new InvalidBearerTokenException("Invalid token version");
        }
        long id;
        try {
            id = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException failure) {
            throw new InvalidBearerTokenException("Invalid token subject");
        }
        UserAuthState user = users.findAuthState(id).orElseThrow(() -> new InvalidBearerTokenException("Unknown user"));
        if (!user.active() || user.tokenVersion() != ((Number) version).longValue()) {
            throw new InvalidBearerTokenException("Inactive token");
        }
        return UsernamePasswordAuthenticationToken.authenticated(user.profile(), jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
    }

    private static void unauthenticated(ObjectMapper mapper, HttpServletResponse response) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        error(mapper, response, 401, "UNAUTHENTICATED", "请登录或重新登录");
    }

    private static void error(ObjectMapper mapper, HttpServletResponse response,
                              int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), ApiResponse.error(code, message));
    }
}
