package com.yliu22520.iotota.diagnosis;

import java.util.Objects;

/** Text-only request sent to a model adapter after the domain has assembled structured evidence. */
public record DiagnosticModelRequest(String prompt, DiagnosticModelConfiguration configuration) {

    public DiagnosticModelRequest {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(configuration, "configuration");
    }
}
