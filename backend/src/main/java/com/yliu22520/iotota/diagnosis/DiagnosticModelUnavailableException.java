package com.yliu22520.iotota.diagnosis;

/** A real-model run cannot silently degrade to the controlled substitute. */
public class DiagnosticModelUnavailableException extends RuntimeException {

    public static final String API_KEY_MISSING = "REAL_MODEL_API_KEY_MISSING";
    public static final String CALL_FAILED = "REAL_MODEL_CALL_FAILED";

    public DiagnosticModelUnavailableException(String message) {
        super(message);
    }

    public DiagnosticModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
