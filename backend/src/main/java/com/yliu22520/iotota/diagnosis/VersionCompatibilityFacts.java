package com.yliu22520.iotota.diagnosis;

/** The immutable facts consumed by the authoritative version rule. */
public record VersionCompatibilityFacts(
        String deviceModel,
        String currentVersion,
        String targetVersion,
        String targetReleaseStatus,
        String compatibleModels) {
}
