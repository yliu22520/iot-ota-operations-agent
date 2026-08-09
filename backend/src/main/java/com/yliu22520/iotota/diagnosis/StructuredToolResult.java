package com.yliu22520.iotota.diagnosis;

import java.time.Instant;

/** Stable structured boundary between the diagnostic workflow and a domain tool. */
public record StructuredToolResult<T>(
        String toolName,
        boolean success,
        String evidenceId,
        String source,
        Instant observedAt,
        T data,
        String error) {

    public static <T> StructuredToolResult<T> success(String toolName, String evidenceId, String source,
                                                      Instant observedAt, T data) {
        return new StructuredToolResult<>(toolName, true, evidenceId, source, observedAt, data, null);
    }

    public static <T> StructuredToolResult<T> failure(String toolName, String evidenceId, String source,
                                                      Instant observedAt, String error) {
        return new StructuredToolResult<>(toolName, false, evidenceId, source, observedAt, null, error);
    }
}
