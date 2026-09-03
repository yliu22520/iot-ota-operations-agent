package com.yliu22520.iotota.diagnosis;

import java.time.Duration;

public final class LiveDiagnosticRateLimitException extends RuntimeException {

    private final Duration retryAfter;
    private final int remainingDaily;

    public LiveDiagnosticRateLimitException(String message, Duration retryAfter, int remainingDaily) {
        super(message);
        this.retryAfter = retryAfter;
        this.remainingDaily = remainingDaily;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    public int remainingDaily() {
        return remainingDaily;
    }
}
