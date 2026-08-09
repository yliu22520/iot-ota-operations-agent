package com.yliu22520.iotota.diagnosis;

import java.time.Instant;

public record FirmwareVersionToolData(String id, String version, String releaseStatus,
                                      String compatibleModels, String checksum, Instant releasedAt) {
}
