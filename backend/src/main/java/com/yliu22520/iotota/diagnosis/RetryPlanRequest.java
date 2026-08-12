package com.yliu22520.iotota.diagnosis;

import java.util.List;
import java.util.UUID;

public record RetryPlanRequest(
        UUID diagnosticTaskId,
        UUID upgradeTaskId,
        long taskVersion,
        String taskStatus,
        String failureCode,
        boolean deviceOnline,
        boolean versionCompatible,
        int retryCount,
        int maxRetries,
        int affectedTaskCount,
        String messageSendStatus,
        String callbackStatus,
        String targetVersion,
        List<String> evidenceRefs,
        String actor) {
}
