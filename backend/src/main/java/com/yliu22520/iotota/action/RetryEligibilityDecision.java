package com.yliu22520.iotota.action;

import java.util.List;

public record RetryEligibilityDecision(boolean eligible, String status, List<String> reasonCodes) {

    public static RetryEligibilityDecision eligible(String reasonCode) {
        return new RetryEligibilityDecision(true, "ELIGIBLE", List.of(reasonCode));
    }

    public static RetryEligibilityDecision forbidden(String reasonCode) {
        return new RetryEligibilityDecision(false, "FORBIDDEN", List.of(reasonCode));
    }
}
