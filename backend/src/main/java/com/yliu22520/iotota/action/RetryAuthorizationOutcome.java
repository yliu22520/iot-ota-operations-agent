package com.yliu22520.iotota.action;

public record RetryAuthorizationOutcome(RetryExecution execution, boolean newExecution, String rejectionCode) {

    public static RetryAuthorizationOutcome accepted(RetryExecution execution, boolean newExecution) {
        return new RetryAuthorizationOutcome(execution, newExecution, null);
    }

    public static RetryAuthorizationOutcome rejected(String code) {
        return new RetryAuthorizationOutcome(null, false, code);
    }
}
