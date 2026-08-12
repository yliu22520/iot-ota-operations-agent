package com.yliu22520.iotota.diagnosis;

import java.util.Objects;

/** Real DeepSeek explanation adapter. It has no fallback path and cannot authorize an action. */
public final class DeepSeekDiagnosticChatModel implements DiagnosticChatModel {

    private final DiagnosticModelClient client;
    private final DiagnosticModelConfiguration configuration;

    public DeepSeekDiagnosticChatModel(DiagnosticModelClient client,
                                       DiagnosticModelConfiguration configuration) {
        this.client = Objects.requireNonNull(client, "client");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        if (!"deepseek".equals(configuration.provider())) {
            throw new IllegalArgumentException("DeepSeek adapter requires the deepseek provider");
        }
    }

    @Override
    public DiagnosticExplanation explain(DiagnosticExplanationRequest request) {
        DiagnosticModelResponse response = client.complete(new DiagnosticModelRequest(
                DiagnosticPrompt.render(request, configuration), configuration));
        if (response == null) {
            throw new DiagnosticModelUnavailableException("REAL_MODEL_CALL_FAILED: empty model response");
        }
        return new DiagnosticExplanation(response.text().strip(), response.inputTokens(), response.outputTokens());
    }

    @Override
    public String modelId() {
        return configuration.modelId();
    }

    public DiagnosticModelConfiguration configuration() {
        return configuration;
    }
}
