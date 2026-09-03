package com.yliu22520.iotota.diagnosis;

import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;

/** Maps provider-neutral release settings to the Gemini 3.x request contract. */
final class GoogleGenAiDiagnosticModelOptions {

    private GoogleGenAiDiagnosticModelOptions() {
    }

    static GoogleGenAiChatOptions from(DiagnosticModelConfiguration configuration) {
        return GoogleGenAiChatOptions.builder()
                .model(configuration.modelId())
                // Gemini 3.x uses thinkingLevel; thinkingBudget belongs to Gemini 2.5.
                .thinkingLevel(thinkingLevel(configuration.reasoningTier()))
                // Gemini 3.x rejects the legacy temperature/topP sampling parameters.
                .maxOutputTokens(configuration.limits().maxOutputTokens())
                .internalToolExecutionEnabled(false)
                .build();
    }

    private static GoogleGenAiThinkingLevel thinkingLevel(String reasoningTier) {
        return switch (reasoningTier) {
            case "HIGH" -> GoogleGenAiThinkingLevel.HIGH;
            case "MEDIUM" -> GoogleGenAiThinkingLevel.MEDIUM;
            case "LOW" -> GoogleGenAiThinkingLevel.LOW;
            case "MINIMAL" -> GoogleGenAiThinkingLevel.MINIMAL;
            default -> throw new IllegalArgumentException("Unsupported Gemini reasoning tier: " + reasoningTier);
        };
    }
}
