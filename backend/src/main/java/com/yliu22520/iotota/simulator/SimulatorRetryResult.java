package com.yliu22520.iotota.simulator;

import java.time.Instant;
import java.util.UUID;

public record SimulatorRetryResult(UUID attemptId, String status, boolean idempotentReplay, Instant acceptedAt) {
}
