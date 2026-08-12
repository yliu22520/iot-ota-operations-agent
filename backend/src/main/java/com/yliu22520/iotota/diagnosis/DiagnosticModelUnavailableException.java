package com.yliu22520.iotota.diagnosis;

/** A real-model run cannot silently degrade to the controlled substitute. */
public class DiagnosticModelUnavailableException extends RuntimeException {

    public DiagnosticModelUnavailableException(String message) {
        super(message);
    }

    public DiagnosticModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
