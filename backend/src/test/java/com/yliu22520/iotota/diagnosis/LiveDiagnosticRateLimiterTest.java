package com.yliu22520.iotota.diagnosis;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveDiagnosticRateLimiterTest {

    @Test
    void rejectsTheFourthLiveDiagnosisWithinOneMinute() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-18T00:00:00Z"));
        LiveDiagnosticRateLimiter limiter = new LiveDiagnosticRateLimiter(clock, "deepseek", 20, 3);

        limiter.acquire();
        limiter.acquire();
        limiter.acquire();

        assertThatThrownBy(limiter::acquire)
                .isInstanceOfSatisfying(LiveDiagnosticRateLimitException.class, exception -> {
                    assertThat(exception.remainingDaily()).isEqualTo(17);
                    assertThat(exception.retryAfter()).isEqualTo(Duration.ofMinutes(1));
                });

        clock.advance(Duration.ofMinutes(1));
        limiter.acquire();
    }

    @Test
    void resetsDailyQuotaAtUtcMidnight() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-18T23:59:50Z"));
        LiveDiagnosticRateLimiter limiter = new LiveDiagnosticRateLimiter(clock, "deepseek", 2, 20);

        limiter.acquire();
        limiter.acquire();
        assertThatThrownBy(limiter::acquire)
                .isInstanceOf(LiveDiagnosticRateLimitException.class);

        clock.advance(Duration.ofSeconds(11));
        limiter.acquire();
    }

    @Test
    void controlledModelDoesNotConsumePublicDemoQuota() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-18T00:00:00Z"));
        LiveDiagnosticRateLimiter limiter = new LiveDiagnosticRateLimiter(clock, "controlled", 1, 1);

        limiter.acquire();
        limiter.acquire();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
