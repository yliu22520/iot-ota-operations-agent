package com.yliu22520.iotota.action;

import java.util.UUID;

public record RetryEligibilityFacts(
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
        String callbackStatus) {
}
