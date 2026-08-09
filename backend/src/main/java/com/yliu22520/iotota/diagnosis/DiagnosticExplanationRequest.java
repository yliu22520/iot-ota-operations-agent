package com.yliu22520.iotota.diagnosis;

import java.util.List;

public record DiagnosticExplanationRequest(VersionCompatibilityFacts facts,
                                           VersionCompatibilityDecision ruleDecision,
                                           List<EvidenceRef> evidenceRefs) {
}
