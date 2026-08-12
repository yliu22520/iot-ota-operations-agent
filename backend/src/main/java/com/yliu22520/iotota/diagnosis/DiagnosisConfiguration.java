package com.yliu22520.iotota.diagnosis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class DiagnosisConfiguration {

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
