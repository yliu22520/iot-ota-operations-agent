package com.yliu22520.iotota.identity;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginAttemptLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final Duration BLOCK = Duration.ofMinutes(15);

    private final Clock clock;
    private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String clientIp) {
        AttemptState state = attempts.get(clientIp);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            Instant now = Instant.now(clock);
            state.removeExpired(now);
            if (state.blockedUntil != null && state.blockedUntil.isAfter(now)) {
                return true;
            }
            if (state.blockedUntil != null) {
                state.blockedUntil = null;
            }
            return false;
        }
    }

    public void registerFailure(String clientIp) {
        AttemptState state = attempts.computeIfAbsent(clientIp, ignored -> new AttemptState());
        synchronized (state) {
            Instant now = Instant.now(clock);
            state.removeExpired(now);
            state.failures.addLast(now);
            if (state.failures.size() >= MAX_FAILURES) {
                state.blockedUntil = now.plus(BLOCK);
                state.failures.clear();
            }
        }
    }

    public void clear(String clientIp) {
        attempts.remove(clientIp);
    }

    private static final class AttemptState {
        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant blockedUntil;

        private void removeExpired(Instant now) {
            Instant threshold = now.minus(WINDOW);
            while (!failures.isEmpty() && failures.peekFirst().isBefore(threshold)) {
                failures.removeFirst();
            }
        }
    }
}
