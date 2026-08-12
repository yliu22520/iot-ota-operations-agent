package com.yliu22520.iotota.action;

import java.time.Instant;
import java.util.UUID;

public record RetryExecutionView(
        UUID diagnosticTaskId,
        UUID planId,
        int planVersion,
        UUID executionId,
        String diagnosticState,
        String executionStatus,
        String verificationStatus,
        String idempotencyKey,
        boolean idempotentReplay,
        Instant updatedAt) {
}
