package com.yliu22520.iotota.diagnosis;

import java.time.Instant;
import java.util.UUID;

public record UpgradeTaskToolData(UUID id, String status, String failureCode, String failureSummary,
                                  Instant failedAt, String deviceId, String firmwareVersionId,
                                  long taskVersion, int retryCount, int maxRetries) {
}
