package com.yliu22520.iotota.diagnosis;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatOptions;

import java.util.Objects;

/** Spring AI text-only transport adapter; provider metadata is reduced to non-sensitive token telemetry. */
public final class SpringAiDeepSeekModelClient implements DiagnosticModelClient {

    private final ChatModel chatModel;

    public SpringAiDeepSeekModelClient(ChatModel chatModel) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
    }

    @Override
    public DiagnosticModelResponse complete(DiagnosticModelRequest request) {
        try {
            DiagnosticModelConfiguration configuration = request.configuration();
            DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                    .model(configuration.modelId())
                    .temperature(configuration.temperature())
                    .topP(configuration.topP())
                    .maxTokens(configuration.limits().maxOutputTokens())
                    .build();
            ChatResponse response = chatModel.call(new Prompt(request.prompt(), options));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null
                    || response.getResult().getOutput().getText() == null
                    || response.getResult().getOutput().getText().isBlank()) {
                throw new DiagnosticModelUnavailableException("REAL_MODEL_CALL_FAILED: empty model response");
            }
            int inputTokens = response.getMetadata() != null && response.getMetadata().getUsage() != null
                    && response.getMetadata().getUsage().getPromptTokens() != null
                    ? response.getMetadata().getUsage().getPromptTokens()
                    : estimateTokens(request.prompt());
            int outputTokens = response.getMetadata() != null && response.getMetadata().getUsage() != null
                    && response.getMetadata().getUsage().getCompletionTokens() != null
                    ? response.getMetadata().getUsage().getCompletionTokens()
                    : estimateTokens(response.getResult().getOutput().getText());
            return new DiagnosticModelResponse(response.getResult().getOutput().getText(), inputTokens, outputTokens);
        } catch (DiagnosticModelUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DiagnosticModelUnavailableException("REAL_MODEL_CALL_FAILED: DeepSeek request failed", exception);
        }
    }

    private int estimateTokens(String text) {
        return Math.max(1, (text.length() + 3) / 4);
    }
}
