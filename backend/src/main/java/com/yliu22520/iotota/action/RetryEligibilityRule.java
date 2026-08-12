package com.yliu22520.iotota.action;

import org.springframework.stereotype.Component;

@Component
public class RetryEligibilityRule {

    public RetryEligibilityDecision evaluate(RetryEligibilityFacts facts) {
        if (!"FINAL_FAILURE".equals(facts.taskStatus())) {
            return RetryEligibilityDecision.forbidden("TASK_NOT_FINAL_FAILURE");
        }
        if (!"CALLBACK_TIMEOUT".equals(facts.failureCode())) {
            return RetryEligibilityDecision.forbidden("FAILURE_NOT_RETRYABLE");
        }
        if (!facts.deviceOnline()) {
            return RetryEligibilityDecision.forbidden("DEVICE_OFFLINE");
        }
        if (!facts.versionCompatible()) {
            return RetryEligibilityDecision.forbidden("VERSION_INCOMPATIBLE");
        }
        if (facts.retryCount() >= facts.maxRetries()) {
            return RetryEligibilityDecision.forbidden("RETRY_LIMIT_REACHED");
        }
        if (facts.affectedTaskCount() != 1) {
            return RetryEligibilityDecision.forbidden("NOT_SINGLE_TASK");
        }
        if (!"SENT".equals(facts.messageSendStatus()) || !"TIMEOUT".equals(facts.callbackStatus())) {
            return RetryEligibilityDecision.forbidden("CALLBACK_TIMEOUT_NOT_CONFIRMED");
        }
        return RetryEligibilityDecision.eligible("CALLBACK_TIMEOUT_RETRY_ALLOWED");
    }
}
