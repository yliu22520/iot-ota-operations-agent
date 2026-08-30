package com.yliu22520.iotota.diagnosis;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiGoogleGenAiModelClientTest {

    @Test
    void missingApiKeyProducesAnExplicitUnavailableModelWithoutFallback() {
        DiagnosticModelClient client = GoogleGenAiDiagnosticModelClientFactory.fromApiKey(
                "", DiagnosticModelConfiguration.releaseBaseline());

        assertThatThrownBy(() -> client.complete(new DiagnosticModelRequest(
                "prompt", DiagnosticModelConfiguration.releaseBaseline())))
                .isInstanceOf(DiagnosticModelUnavailableException.class)
                .hasMessage(DiagnosticModelUnavailableException.API_KEY_MISSING);
    }

    @Test
    void mapsProviderNeutralRequestToPinnedGoogleOptionsAndSanitizedResponse() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("structured answer")))));
        SpringAiGoogleGenAiModelClient client = new SpringAiGoogleGenAiModelClient(chatModel);
        DiagnosticModelConfiguration configuration = DiagnosticModelConfiguration.releaseBaseline();

        DiagnosticModelResponse response = client.complete(new DiagnosticModelRequest("structured prompt", configuration));

        assertThat(response.text()).isEqualTo("structured answer");
        assertThat(response.inputTokens()).isPositive();
        assertThat(response.outputTokens()).isPositive();
        org.mockito.ArgumentCaptor<Prompt> prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        org.mockito.Mockito.verify(chatModel).call(prompt.capture());
        GoogleGenAiChatOptions options = (GoogleGenAiChatOptions) prompt.getValue().getOptions();
        assertThat(options.getModel()).isEqualTo("gemini-2.5-flash");
        assertThat(options.getThinkingBudget()).isEqualTo(4_096);
        assertThat(options.getTemperature()).isEqualTo(0.0d);
        assertThat(options.getTopP()).isEqualTo(1.0d);
        assertThat(options.getMaxOutputTokens()).isEqualTo(4096);
        assertThat(options.getInternalToolExecutionEnabled()).isFalse();
    }

    @Test
    void translatesProviderFailuresWithoutExposingProviderMessage() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("provider-secret-details"));
        SpringAiGoogleGenAiModelClient client = new SpringAiGoogleGenAiModelClient(chatModel);

        assertThatThrownBy(() -> client.complete(new DiagnosticModelRequest(
                "prompt", DiagnosticModelConfiguration.releaseBaseline())))
                .isInstanceOf(DiagnosticModelUnavailableException.class)
                .hasMessage("REAL_MODEL_CALL_FAILED: Google GenAI request failed")
                .hasRootCauseMessage("provider-secret-details");
    }
}
