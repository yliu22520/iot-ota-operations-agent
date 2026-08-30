package com.yliu22520.iotota.diagnosis;

import java.util.Objects;

/** Real-provider explanation adapter. It has no fallback path and cannot authorize an action. */
public final class ProviderDiagnosticChatModel implements DiagnosticChatModel {

    private final DiagnosticModelClient client;
    private final DiagnosticModelConfiguration configuration;

    public ProviderDiagnosticChatModel(DiagnosticModelClient client,
                                       DiagnosticModelConfiguration configuration) {
        this.client = Objects.requireNonNull(client, "client");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        if ("controlled".equalsIgnoreCase(configuration.provider())) {
            throw new IllegalArgumentException("Real adapter requires a non-controlled provider");
        }
    }

    @Override
    public DiagnosticExplanation explain(DiagnosticExplanationRequest request) {
        DiagnosticModelResponse response = client.complete(new DiagnosticModelRequest(
                DiagnosticPrompt.render(request, configuration), configuration));
        if (response == null) {
            throw new DiagnosticModelUnavailableException(
                    DiagnosticModelUnavailableException.CALL_FAILED + ": empty model response");
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
