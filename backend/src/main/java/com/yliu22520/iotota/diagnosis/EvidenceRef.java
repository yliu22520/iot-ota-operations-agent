package com.yliu22520.iotota.diagnosis;

import java.time.Instant;

/** A stable, user-visible pointer to one observed fact. */
public record EvidenceRef(String evidenceId, String source, Instant observedAt, String summary) {
}
