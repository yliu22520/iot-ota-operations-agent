package com.yliu22520.iotota.diagnosis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;

@Component
public class LiveDiagnosticRateLimiter {

    private final Clock clock;
    private final boolean enabled;
    private final int dailyLimit;
    private final int minuteLimit;
    private final Deque<Instant> minuteStarts = new ArrayDeque<>();
    private LocalDate currentDay;
    private int dailyStarts;

    public LiveDiagnosticRateLimiter(Clock clock,
                                     @Value("${diagnosis.model.provider:controlled}") String provider,
                                     @Value("${demo.live-diagnosis.daily-limit:20}") int dailyLimit,
                                     @Value("${demo.live-diagnosis.per-minute-limit:3}") int minuteLimit) {
        this.clock = clock;
        this.enabled = "deepseek".equalsIgnoreCase(provider);
        this.dailyLimit = dailyLimit;
        this.minuteLimit = minuteLimit;
        this.currentDay = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    public synchronized void acquire() {
        if (!enabled) {
            return;
        }

        Instant now = Instant.now(clock);
        LocalDate day = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (!day.equals(currentDay)) {
            currentDay = day;
            dailyStarts = 0;
            minuteStarts.clear();
        }

        Instant minuteBoundary = now.minus(Duration.ofMinutes(1));
        while (!minuteStarts.isEmpty() && !minuteStarts.peekFirst().isAfter(minuteBoundary)) {
            minuteStarts.removeFirst();
        }

        int remainingDaily = Math.max(0, dailyLimit - dailyStarts);
        if (dailyStarts >= dailyLimit) {
            Instant nextDay = day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            throw new LiveDiagnosticRateLimitException(
                    "Daily live diagnosis quota exhausted",
                    Duration.between(now, nextDay),
                    0);
        }

        if (minuteStarts.size() >= minuteLimit) {
            Instant retryAt = minuteStarts.peekFirst().plus(Duration.ofMinutes(1));
            throw new LiveDiagnosticRateLimitException(
                    "Live diagnosis rate limit exceeded",
                    Duration.between(now, retryAt),
                    remainingDaily);
        }

        minuteStarts.addLast(now);
        dailyStarts++;
    }
}
