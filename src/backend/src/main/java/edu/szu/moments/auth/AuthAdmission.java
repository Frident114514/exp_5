package edu.szu.moments.auth;

import edu.szu.moments.api.ApiFailure;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.function.LongSupplier;

/** Instance-local bounds: password work cannot occupy the identity-read capacity. */
@Component
final class AuthAdmission {
    private final Semaphore passwordSlots;
    private final Semaphore identitySlots;
    private final double rate;
    private final int burst;
    private final LongSupplier ticker;
    private double tokens;
    private long lastRefill;

    @Autowired
    AuthAdmission(@Value("${moments.auth.max-concurrent:4}") int passwordCapacity,
                  @Value("${moments.auth.me-max-concurrent:64}") int identityCapacity,
                  @Value("${moments.auth.requests-per-second:10}") double rate,
                  @Value("${moments.auth.burst:20}") int burst) {
        this(passwordCapacity, identityCapacity, rate, burst, System::nanoTime);
    }

    AuthAdmission(int passwordCapacity, int identityCapacity, double rate, int burst, LongSupplier ticker) {
        if (passwordCapacity < 1 || identityCapacity < 1 || !Double.isFinite(rate) || rate <= 0 || burst < 1) {
            throw new IllegalArgumentException("Authentication capacity and rate must be positive and finite");
        }
        this.passwordSlots = new Semaphore(passwordCapacity);
        this.identitySlots = new Semaphore(identityCapacity);
        this.rate = rate;
        this.burst = burst;
        this.tokens = burst;
        this.ticker = ticker;
        this.lastRefill = ticker.getAsLong();
    }

    AutoCloseable admit(boolean passwordRequest) {
        Semaphore slots = passwordRequest ? passwordSlots : identitySlots;
        if (!slots.tryAcquire()) {
            throw overloaded(passwordRequest);
        }
        if (passwordRequest && !takeToken()) {
            slots.release();
            throw overloaded(true);
        }
        return slots::release;
    }

    private synchronized boolean takeToken() {
        long now = ticker.getAsLong();
        tokens = Math.min(burst, tokens + Math.max(0, now - lastRefill) / 1_000_000_000.0 * rate);
        lastRefill = now;
        if (tokens < 1) return false;
        tokens -= 1;
        return true;
    }

    private static ApiFailure overloaded(boolean passwordRequest) {
        return passwordRequest
                ? new ApiFailure(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "认证请求繁忙，请稍后重试")
                : new ApiFailure(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_BUSY", "服务繁忙，请稍后重试");
    }
}
