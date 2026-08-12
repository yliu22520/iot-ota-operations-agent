package com.yliu22520.iotota.diagnosis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Deterministic, network-free substitute used by CI and the local simulated platform. */
@Component
@ConditionalOnProperty(name = "diagnosis.model.provider", havingValue = "controlled", matchIfMissing = true)
public class ControlledDiagnosticChatModel implements DiagnosticChatModel {

    @Override
    public DiagnosticExplanation explain(DiagnosticExplanationRequest request) {
        VersionCompatibilityDecision decision = request.ruleDecision();
        return new DiagnosticExplanation("Backend compatibility rule " + decision.reasonCode()
                + " is authoritative; the explanation cannot change the rule result.");
    }

    @Override
    public String modelId() {
        return "controlled-diagnostic-explainer-v1";
    }
}
