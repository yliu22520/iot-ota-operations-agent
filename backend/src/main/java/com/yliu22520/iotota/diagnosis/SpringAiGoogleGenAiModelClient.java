package com.yliu22520.iotota.diagnosis;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.DisposableBean;

import java.util.Objects;

/** Spring AI Google GenAI transport adapter; provider details stop at this boundary. */
public final class SpringAiGoogleGenAiModelClient implements DiagnosticModelClient, DisposableBean {

    private final ChatModel chatModel;
    private final AutoCloseable resource;

    public SpringAiGoogleGenAiModelClient(ChatModel chatModel) {
        this(chatModel, null);
    }

    SpringAiGoogleGenAiModelClient(ChatModel chatModel, AutoCloseable resource) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.resource = resource;
    }

    @Override
    public DiagnosticModelResponse complete(DiagnosticModelRequest request) {
        try {
            DiagnosticModelConfiguration configuration = request.configuration();
            GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                    .model(configuration.modelId())
                    .thinkingBudget(configuration.reasoningBudgetTokens())
                    .temperature(configuration.temperature())
                    .topP(configuration.topP())
                    .maxOutputTokens(configuration.limits().maxOutputTokens())
                    .internalToolExecutionEnabled(false)
                    .build();
            ChatResponse response = chatModel.call(new Prompt(request.prompt(), options));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null
                    || response.getResult().getOutput().getText() == null
                    || response.getResult().getOutput().getText().isBlank()) {
                throw new DiagnosticModelUnavailableException(
                        DiagnosticModelUnavailableException.CALL_FAILED + ": empty model response");
            }
            int inputTokens = response.getMetadata() != null && response.getMetadata().getUsage() != null
                    && response.getMetadata().getUsage().getPromptTokens() != null
                    && response.getMetadata().getUsage().getPromptTokens() > 0
                    ? response.getMetadata().getUsage().getPromptTokens()
                    : estimateTokens(request.prompt());
            int outputTokens = response.getMetadata() != null && response.getMetadata().getUsage() != null
                    && response.getMetadata().getUsage().getCompletionTokens() != null
                    && response.getMetadata().getUsage().getCompletionTokens() > 0
                    ? response.getMetadata().getUsage().getCompletionTokens()
                    : estimateTokens(response.getResult().getOutput().getText());
            return new DiagnosticModelResponse(response.getResult().getOutput().getText(), inputTokens, outputTokens);
        } catch (DiagnosticModelUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DiagnosticModelUnavailableException(
                    DiagnosticModelUnavailableException.CALL_FAILED + ": Google GenAI request failed", exception);
        }
    }

    @Override
    public void destroy() throws Exception {
        Exception closeFailure = null;
        try {
            if (chatModel instanceof DisposableBean disposableBean) {
                disposableBean.destroy();
            }
        } catch (Exception exception) {
            closeFailure = exception;
        }
        try {
            if (resource != null) {
                resource.close();
            }
        } catch (Exception exception) {
            if (closeFailure == null) {
                closeFailure = exception;
            }
        }
        if (closeFailure != null) {
            throw closeFailure;
        }
    }

    private int estimateTokens(String text) {
        return Math.max(1, (text.length() + 3) / 4);
    }
}
