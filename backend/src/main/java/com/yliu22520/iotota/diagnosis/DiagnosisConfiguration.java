package com.yliu22520.iotota.diagnosis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;

import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class DiagnosisConfiguration {

    @Bean
    DiagnosticModelConfiguration diagnosticModelConfiguration(
            @Value("${diagnosis.model.provider:controlled}") String provider) {
        if ("controlled".equals(provider)) {
            return DiagnosticModelConfiguration.controlled();
        }
        if ("deepseek".equals(provider)) {
            return DiagnosticModelConfiguration.runtimeRelease();
        }
        throw new IllegalArgumentException("Unsupported diagnosis model provider: " + provider);
    }

    @Bean
    @ConditionalOnProperty(name = "diagnosis.model.provider", havingValue = "deepseek")
    DiagnosticModelClient deepSeekModelClient(
            DiagnosticModelConfiguration configuration,
            @Value("${diagnosis.model.base-url:https://api.deepseek.com}") String baseUrl) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return request -> {
                throw new DiagnosticModelUnavailableException("MISSING_DEEPSEEK_API_KEY");
            };
        }
        DeepSeekApi api = DeepSeekApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        DeepSeekChatOptions options = DeepSeekChatOptions.builder()
                .model(configuration.modelId())
                .temperature(configuration.temperature())
                .topP(configuration.topP())
                .maxTokens(configuration.limits().maxOutputTokens())
                .build();
        ChatModel chatModel = DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .defaultOptions(options)
                .build();
        return new SpringAiDeepSeekModelClient(chatModel);
    }

    @Bean
    @ConditionalOnProperty(name = "diagnosis.model.provider", havingValue = "deepseek")
    DiagnosticChatModel deepSeekDiagnosticChatModel(DiagnosticModelClient client,
                                                    DiagnosticModelConfiguration configuration) {
        return new DeepSeekDiagnosticChatModel(client, configuration);
    }

    @Bean
    @ConditionalOnExpression("'${diagnosis.evaluation.enabled:false}' == 'true' "
            + "and '${diagnosis.model.provider:controlled}' == 'deepseek'")
    DiagnosticEvaluationApplicationRunner diagnosticEvaluationApplicationRunner(
            DiagnosticModelClient client,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${diagnosis.evaluation.output-directory:${java.io.tmpdir}/iot-ota-evaluation}") String outputDirectory) {
        return new DiagnosticEvaluationApplicationRunner(client, objectMapper, Path.of(outputDirectory), clock);
    }

    @Bean
    DiagnosticStateMachine diagnosticStateMachine() {
        return new DiagnosticStateMachine();
    }

    @Bean
    VersionCompatibilityRule versionCompatibilityRule() {
        return new VersionCompatibilityRule();
    }

    @Bean
    DiagnosticReportFactory diagnosticReportFactory(
            @Value("${knowledge.embedding.model-id}") String embeddingModelId,
            @Value("${knowledge.embedding.model-revision}") String embeddingModelRevision,
            @Value("${knowledge.embedding.model-sha256}") String embeddingModelSha256) {
        return new DiagnosticReportFactory(embeddingModelId, embeddingModelRevision, embeddingModelSha256);
    }

    @Bean(name = "diagnosticExecutor")
    Executor diagnosticExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("diagnostic-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
