package com.yliu22520.iotota.diagnosis;

/** Explanation-only model seam. It cannot alter the backend rule decision. */
public interface DiagnosticChatModel {

    DiagnosticExplanation explain(DiagnosticExplanationRequest request);

    String modelId();
}
