package com.yliu22520.iotota.diagnosis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VersionCompatibilityRuleTest {

    private final VersionCompatibilityRule rule = new VersionCompatibilityRule();

    @Test
    void rejectsAReleasedFirmwareThatDoesNotSupportTheDeviceModel() {
        VersionCompatibilityDecision decision = rule.evaluate(new VersionCompatibilityFacts(
                "EDGE-CAMERA-A", "1.0.0", "2.0.0", "RELEASED", "SENSOR-HUB-B"));

        assertThat(decision.compatible()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("TARGET_MODEL_NOT_SUPPORTED");
        assertThat(decision.summary()).contains("EDGE-CAMERA-A", "2.0.0");
    }

    @Test
    void acceptsAFirmwareWhenTheDeviceModelIsListedAmongCompatibleModels() {
        VersionCompatibilityDecision decision = rule.evaluate(new VersionCompatibilityFacts(
                "SENSOR-HUB-B", "1.0.0", "1.1.0", "RELEASED", "EDGE-CAMERA-A,SENSOR-HUB-B"));

        assertThat(decision.compatible()).isTrue();
        assertThat(decision.reasonCode()).isEqualTo("COMPATIBLE");
    }

    @Test
    void rejectsFirmwareThatIsNotReleasedBeforeCheckingModelCompatibility() {
        VersionCompatibilityDecision decision = rule.evaluate(new VersionCompatibilityFacts(
                "SENSOR-HUB-B", "1.0.0", "1.1.0", "DRAFT", "SENSOR-HUB-B"));

        assertThat(decision.compatible()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("FIRMWARE_NOT_RELEASED");
    }
}
