package com.yliu22520.iotota.diagnosis;

import java.time.Instant;

public record MessageStateToolData(Long id, String messageType, String sendStatus,
                                   String callbackStatus, Instant observedAt, String detail) {
}
