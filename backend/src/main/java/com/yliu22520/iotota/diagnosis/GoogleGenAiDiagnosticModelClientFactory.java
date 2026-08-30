package com.yliu22520.iotota.diagnosis;

import com.google.genai.Client;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.retry.support.RetryTemplate;

import java.util.Objects;

/** Creates the Google transport in one adapter boundary without exposing credentials to the domain. */
final class GoogleGenAiDiagnosticModelClientFactory {

    static final String API_KEY_ENVIRONMENT_VARIABLE = "GEMINI_API_KEY";

    private GoogleGenAiDiagnosticModelClientFactory() {
    }

    static DiagnosticModelClient fromEnvironment(DiagnosticModelConfiguration configuration) {
        return fromApiKey(System.getenv(API_KEY_ENVIRONMENT_VARIABLE), configuration);
    }

    static DiagnosticModelClient fromApiKey(String apiKey, DiagnosticModelConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        if (apiKey == null || apiKey.isBlank()) {
            return request -> {
                throw new DiagnosticModelUnavailableException(DiagnosticModelUnavailableException.API_KEY_MISSING);
            };
        }

        Client api = Client.builder().apiKey(apiKey).build();
        try {
            GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                    .model(configuration.modelId())
                    .thinkingBudget(configuration.reasoningBudgetTokens())
                    .temperature(configuration.temperature())
                    .topP(configuration.topP())
                    .maxOutputTokens(configuration.limits().maxOutputTokens())
                    .internalToolExecutionEnabled(false)
                    .build();
            ChatModel chatModel = GoogleGenAiChatModel.builder()
                    .genAiClient(api)
                    .defaultOptions(options)
                    .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                    .observationRegistry(ObservationRegistry.NOOP)
                    .build();
            return new SpringAiGoogleGenAiModelClient(chatModel, api);
        } catch (RuntimeException exception) {
            try {
                api.close();
            } catch (Exception ignored) {
                // Preserve the original configuration failure without leaking credential details.
            }
            throw exception;
        }
    }
}
