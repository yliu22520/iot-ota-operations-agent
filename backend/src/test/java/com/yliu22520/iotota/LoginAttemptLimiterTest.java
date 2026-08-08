package com.yliu22520.iotota;

import com.yliu22520.iotota.identity.LoginAttemptLimiter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptLimiterTest {

    @Test
    void blocksAnIpAfterFiveFailuresAndClearsAfterSuccessfulLogin() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(
                Clock.fixed(Instant.parse("2026-08-09T00:00:00Z"), ZoneOffset.UTC));

        for (int attempt = 0; attempt < 4; attempt++) {
            limiter.registerFailure("192.0.2.10");
            assertThat(limiter.isBlocked("192.0.2.10")).isFalse();
        }
        limiter.registerFailure("192.0.2.10");

        assertThat(limiter.isBlocked("192.0.2.10")).isTrue();
        limiter.clear("192.0.2.10");
        assertThat(limiter.isBlocked("192.0.2.10")).isFalse();
    }
}
