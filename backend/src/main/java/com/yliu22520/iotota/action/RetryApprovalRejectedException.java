package com.yliu22520.iotota.action;

public class RetryApprovalRejectedException extends RuntimeException {

    private final String code;

    public RetryApprovalRejectedException(String code) {
        super("Retry approval rejected: " + code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
