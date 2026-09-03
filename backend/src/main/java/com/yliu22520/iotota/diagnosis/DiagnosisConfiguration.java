package com.yliu22520.iotota.diagnosis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class DiagnosisConfiguration {

    @Bean
    DiagnosticModelConfiguration diagnosticModelConfiguration(
            @Value("${diagnosis.model.provider:controlled}") String provider,
            @Value("${diagnosis.model.name:gemini-3.1-flash-lite}") String modelName) {
        return buildDiagnosticModelConfiguration(provider, modelName);
    }

    DiagnosticModelConfiguration diagnosticModelConfiguration(String provider) {
        return buildDiagnosticModelConfiguration(provider, DiagnosticModelConfiguration.RELEASE_MODEL_ID);
    }

    private DiagnosticModelConfiguration buildDiagnosticModelConfiguration(String provider, String modelName) {
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if ("controlled".equals(normalizedProvider)) {
            return DiagnosticModelConfiguration.controlled();
        }
        if (DiagnosticModelConfiguration.GEMINI_PROVIDER.equals(normalizedProvider)) {
            return DiagnosticModelConfiguration.runtimeRelease(modelName);
        }
        throw new IllegalArgumentException("Unsupported diagnosis model provider: " + provider);
    }

    @Bean
    @ConditionalOnExpression("'${diagnosis.model.provider:controlled}'.trim().equalsIgnoreCase('gemini')")
    DiagnosticModelClient diagnosticModelClient(DiagnosticModelConfiguration configuration) {
        return GoogleGenAiDiagnosticModelClientFactory.fromEnvironment(configuration);
    }

    @Bean
    @ConditionalOnExpression("'${diagnosis.model.provider:controlled}'.trim().equalsIgnoreCase('gemini')")
    DiagnosticChatModel realDiagnosticChatModel(DiagnosticModelClient client,
                                                DiagnosticModelConfiguration configuration) {
        return new ProviderDiagnosticChatModel(client, configuration);
    }

    @Bean
    @ConditionalOnExpression("'${diagnosis.evaluation.enabled:false}' == 'true' "
            + "and '${diagnosis.model.provider:controlled}'.trim().equalsIgnoreCase('gemini')")
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
