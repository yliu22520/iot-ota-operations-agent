package com.yliu22520.iotota.diagnosis;

/** A deterministic rule result. Model output is not part of this decision. */
public record VersionCompatibilityDecision(boolean compatible, String reasonCode, String summary) {
}
