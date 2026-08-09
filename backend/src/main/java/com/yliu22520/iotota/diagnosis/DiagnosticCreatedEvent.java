package com.yliu22520.iotota.diagnosis;

import java.util.UUID;

public record DiagnosticCreatedEvent(UUID diagnosticTaskId, String actor) {
}
