package com.yliu22520.iotota.diagnosis;

/** Provider-neutral boundary used by both the real adapter and deterministic tests. */
public interface DiagnosticModelClient {

    DiagnosticModelResponse complete(DiagnosticModelRequest request);
}
