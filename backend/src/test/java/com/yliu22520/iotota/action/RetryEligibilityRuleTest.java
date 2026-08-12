package com.yliu22520.iotota.action;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RetryEligibilityRuleTest {

    private final RetryEligibilityRule rule = new RetryEligibilityRule();

    @Test
    void permitsOneFinalFailedCallbackTimeoutWhenEveryAuthoritativePreconditionStillHolds() {
        RetryEligibilityDecision decision = rule.evaluate(new RetryEligibilityFacts(
                UUID.fromString("00000000-0000-0000-0000-000000000102"),
                7,
                "FINAL_FAILURE",
                "CALLBACK_TIMEOUT",
                true,
                true,
                1,
                3,
                1,
                "SENT",
                "TIMEOUT"));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.status()).isEqualTo("ELIGIBLE");
        assertThat(decision.reasonCodes()).containsExactly("CALLBACK_TIMEOUT_RETRY_ALLOWED");
    }

    @Test
    void rejectsRetryWhenAnyAuthoritativePreconditionChanged() {
        RetryEligibilityDecision decision = rule.evaluate(new RetryEligibilityFacts(
                UUID.randomUUID(),
                7,
                "FINAL_FAILURE",
                "CALLBACK_TIMEOUT",
                false,
                true,
                1,
                3,
                1,
                "SENT",
                "TIMEOUT"));

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.status()).isEqualTo("FORBIDDEN");
        assertThat(decision.reasonCodes()).containsExactly("DEVICE_OFFLINE");
    }
}
