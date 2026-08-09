package com.yliu22520.iotota.diagnosis;

import java.util.Arrays;

/**
 * Authoritative compatibility policy for the simulated firmware catalogue.
 * Retrieved text or model output must not replace this rule's conclusion.
 */
public class VersionCompatibilityRule {

    public VersionCompatibilityDecision evaluate(VersionCompatibilityFacts facts) {
        if (!"RELEASED".equalsIgnoreCase(facts.targetReleaseStatus())) {
            return new VersionCompatibilityDecision(false, "FIRMWARE_NOT_RELEASED",
                    "Target firmware " + facts.targetVersion() + " is not released");
        }

        boolean modelSupported = Arrays.stream(facts.compatibleModels().split(","))
                .map(String::trim)
                .filter(model -> !model.isBlank())
                .anyMatch(model -> model.equalsIgnoreCase(facts.deviceModel()));
        if (!modelSupported) {
            return new VersionCompatibilityDecision(false, "TARGET_MODEL_NOT_SUPPORTED",
                    "Target firmware " + facts.targetVersion() + " does not support device model "
                            + facts.deviceModel());
        }

        return new VersionCompatibilityDecision(true, "COMPATIBLE",
                "Target firmware " + facts.targetVersion() + " supports device model " + facts.deviceModel());
    }
}
