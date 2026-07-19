package com.lab.backend.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    // 3 attempts allowed, long window, long block — deterministic for the test.
    private LoginRateLimiter limiter() {
        return new LoginRateLimiter(3, 300, 900);
    }

    @Test
    void allowsUpToLimitThenBlocks() {
        LoginRateLimiter rl = limiter();
        String ip = "1.1.1.1", email = "user@lab.local";

        // First two failures stay under the limit.
        rl.checkAllowed(ip, email);
        rl.recordFailure(ip, email);
        rl.checkAllowed(ip, email);
        rl.recordFailure(ip, email);
        rl.checkAllowed(ip, email); // still allowed (2 failures < 3)

        // Third failure trips the block.
        rl.recordFailure(ip, email);
        assertThatThrownBy(() -> rl.checkAllowed(ip, email))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void successResetsTheCounter() {
        LoginRateLimiter rl = limiter();
        String ip = "2.2.2.2", email = "user@lab.local";

        rl.recordFailure(ip, email);
        rl.recordFailure(ip, email);
        rl.recordSuccess(ip, email); // clears counter

        // A fresh failure after reset must not immediately block.
        rl.recordFailure(ip, email);
        assertThatCode(() -> rl.checkAllowed(ip, email)).doesNotThrowAnyException();
    }

    @Test
    void differentKeysAreIndependent() {
        LoginRateLimiter rl = limiter();
        String email = "user@lab.local";

        // Block IP 3.3.3.3 for this email.
        for (int i = 0; i < 3; i++) rl.recordFailure("3.3.3.3", email);
        assertThatThrownBy(() -> rl.checkAllowed("3.3.3.3", email))
                .isInstanceOf(TooManyAttemptsException.class);

        // A different IP for the same email is unaffected.
        assertThatCode(() -> rl.checkAllowed("4.4.4.4", email)).doesNotThrowAnyException();
    }

    @Test
    void retryAfterIsPositive() {
        LoginRateLimiter rl = limiter();
        for (int i = 0; i < 3; i++) rl.recordFailure("5.5.5.5", "u@lab.local");
        assertThatThrownBy(() -> rl.checkAllowed("5.5.5.5", "u@lab.local"))
                .isInstanceOfSatisfying(TooManyAttemptsException.class,
                        ex -> org.assertj.core.api.Assertions.assertThat(ex.getRetryAfterSeconds()).isPositive());
    }
}
