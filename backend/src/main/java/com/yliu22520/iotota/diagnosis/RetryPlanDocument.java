package com.yliu22520.iotota.diagnosis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RetryPlanDocument(
        UUID planId,
        int planVersion,
        UUID diagnosticTaskId,
        UUID upgradeTaskId,
        long taskVersion,
        String taskStatus,
        String targetVersion,
        String failureCode,
        int retryCount,
        int maxRetries,
        boolean deviceOnline,
        boolean versionCompatible,
        String messageSendStatus,
        String callbackStatus,
        String impactScope,
        List<String> preconditions,
        List<String> evidenceRefs,
        String status,
        Instant createdAt,
        Instant expiresAt) {
}
