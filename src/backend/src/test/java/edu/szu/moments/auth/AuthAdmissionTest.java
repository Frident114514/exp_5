package edu.szu.moments.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.szu.moments.api.ApiFailure;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthAdmissionTest {
    @Test
    void concurrentPasswordWorkIsBoundedAndCapacityRecovers() throws Exception {
        var gate = new AuthAdmission(2, 4, 1000, 1000);
        var attempted = new CountDownLatch(32);
        var release = new CountDownLatch(1);
        var accepted = new AtomicInteger();
        var rejected = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(32)) {
            var jobs = new ArrayList<Future<?>>();
            try {
                for (int i = 0; i < 32; i++) {
                    jobs.add(pool.submit(() -> {
                        AutoCloseable permit;
                        try {
                            permit = gate.admit(true);
                            accepted.incrementAndGet();
                        } catch (ApiFailure full) {
                            rejected.incrementAndGet();
                            attempted.countDown();
                            return;
                        }
                        attempted.countDown();
                        try (permit) {
                            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Release timed out");
                        } catch (Exception failure) {
                            throw new AssertionError(failure);
                        }
                    }));
                }
                assertThat(attempted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(accepted.get()).isEqualTo(2);
                assertThat(rejected.get()).isEqualTo(30);
            } finally {
                release.countDown();
            }
            for (var job : jobs) job.get(5, TimeUnit.SECONDS);
        }
        try (var permit = gate.admit(true)) {
            assertThat(permit).isNotNull();
        }
    }

    @Test
    void rateLimitRefillsWithoutSleepingAndDoesNotAccumulateBeyondBurst() throws Exception {
        var clock = new AtomicLong();
        var gate = new AuthAdmission(1, 1, 2, 2, clock::get);
        gate.admit(true).close();
        gate.admit(true).close();
        assertThatThrownBy(() -> gate.admit(true)).isInstanceOfSatisfying(ApiFailure.class,
                failure -> assertThat(failure.status().value()).isEqualTo(429));
        clock.addAndGet(500_000_000);
        gate.admit(true).close();
        assertThatThrownBy(() -> gate.admit(true)).isInstanceOf(ApiFailure.class);
        clock.addAndGet(100_000_000_000L);
        gate.admit(true).close();
        gate.admit(true).close();
        assertThatThrownBy(() -> gate.admit(true)).isInstanceOf(ApiFailure.class);
    }

    @Test
    void saturatedPasswordGateDoesNotBlockIdentityReads() throws Exception {
        var gate = new AuthAdmission(1, 1, 1, 1);
        try (var password = gate.admit(true); var identity = gate.admit(false)) {
            assertThatThrownBy(() -> gate.admit(true)).isInstanceOf(ApiFailure.class);
            assertThatThrownBy(() -> gate.admit(false)).isInstanceOfSatisfying(ApiFailure.class,
                    failure -> assertThat(failure.status().value()).isEqualTo(503));
        }
        // Password rate is still exhausted; identity reads do not consume that rate.
        try (var identity = gate.admit(false)) {
            assertThat(identity).isNotNull();
        }
    }

    @Test
    void ioFailureReleasesAdmissionAndOverloadNeverEntersController() throws Exception {
        var gate = new AuthAdmission(1, 1, 100, 100);
        var filter = new AuthLoadFilter(gate, new ObjectMapper());
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login");
        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> { throw new IOException("client disconnected"); })).isInstanceOf(IOException.class);
        try (var occupied = gate.admit(true)) {
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> { throw new AssertionError("Controller reached"); });
            assertThat(response.getStatus()).isEqualTo(429);
            assertThat(response.getHeader("Retry-After")).isEqualTo("1");
            assertThat(response.getContentAsString()).contains("RATE_LIMITED");
        }
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
    }

    @Test
    void invalidCapacityCannotDisableProtectionSilently() {
        assertThatThrownBy(() -> new AuthAdmission(0, 1, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthAdmission(1, 1, Double.NaN, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void authenticationDatabaseFailureReturns503AndReleasesCapacity() throws Exception {
        var gate = new AuthAdmission(1, 1, 100, 100);
        var filter = new AuthLoadFilter(gate, new ObjectMapper());
        var request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.setServletPath("/api/v1/users/me");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            throw new org.springframework.dao.DataAccessResourceFailureException("database unavailable");
        });
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        assertThat(response.getContentAsString()).contains("SERVICE_BUSY").doesNotContain("database unavailable");
        try (var permit = gate.admit(false)) {
            assertThat(permit).isNotNull();
        }
    }
}
