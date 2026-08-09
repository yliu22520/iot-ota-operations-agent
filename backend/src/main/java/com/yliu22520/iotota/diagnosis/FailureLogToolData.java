package com.yliu22520.iotota.diagnosis;

import java.time.Instant;

public record FailureLogToolData(Long id, Instant observedAt, String level, String code,
                                 String message, String source) {
}
