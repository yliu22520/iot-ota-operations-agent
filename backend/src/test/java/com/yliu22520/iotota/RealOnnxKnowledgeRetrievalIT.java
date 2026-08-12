package com.yliu22520.iotota;

import com.yliu22520.iotota.knowledge.KnowledgeRetriever;
import com.yliu22520.iotota.knowledge.KnowledgeSearchQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Fifteen-case calibration runner for the pinned local multilingual-e5-small model. */
@Testcontainers
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "KNOWLEDGE_REAL_MODEL_URI", matches = ".+")
class RealOnnxKnowledgeRetrievalIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("ota_operations").withUsername("ota").withPassword("ota");

    @Autowired
    private KnowledgeRetriever retriever;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("knowledge.embedding.provider", () -> "onnx");
        registry.add("knowledge.embedding.model-uri", () -> System.getenv("KNOWLEDGE_REAL_MODEL_URI"));
        registry.add("knowledge.embedding.tokenizer-uri", () -> System.getenv("KNOWLEDGE_REAL_TOKENIZER_URI"));
        registry.add("knowledge.search.similarity-threshold", () -> "0.70");
    }

    @Test
    void relevantDocumentsLeadFifteenRepresentativeFaultCases() {
        List<Case> cases = List.of(
                c("VERSION_INCOMPATIBLE", "target model unsupported", "firmware", "ota-version-compatibility"),
                c("VERSION_INCOMPATIBLE", "设备型号不在固件兼容清单", "device", "ota-version-compatibility"),
                c("VERSION_INCOMPATIBLE", "historical incompatible model", "firmware", "case-incompatible-device-model"),
                c("CALLBACK_TIMEOUT", "message sent but callback timed out", "message", "ota-callback-timeout"),
                c("CALLBACK_TIMEOUT", "指令已发送但设备回调超时", "message", "ota-callback-timeout"),
                c("CALLBACK_TIMEOUT", "late callback incident", "message", "case-late-device-callback"),
                c("CHECKSUM_MISMATCH", "download checksum mismatch", "download", "ota-download-checksum"),
                c("CHECKSUM_MISMATCH", "固件包哈希校验失败", "firmware", "ota-download-checksum"),
                c("DOWNLOAD_FAILED", "download interrupted and package incomplete", "download", "ota-download-checksum"),
                c("DEVICE_OFFLINE", "device offline before upgrade", "device", "ota-device-offline-resources"),
                c("STORAGE_LOW", "设备存储空间不足", "device", "ota-device-offline-resources"),
                c("RESOURCE_INSUFFICIENT", "设备离线且存储空间不足，应先恢复在线并清理空间",
                        "device", "ota-device-offline-resources"),
                c("RETRY_REJECTED", "重试属于写操作，必须由操作员明确审批", "retry", "ota-retry-safety"),
                c("RETRY_UNSAFE", "重试次数已耗尽", "retry", "ota-retry-safety"),
                c("TASK_STATE_INVALID", "升级任务不在最终失败状态，不得继续重试", "task", "ota-task-state"));

        int relevantInTopTwo = 0;
        for (Case faultCase : cases) {
            var result = retriever.search(new KnowledgeSearchQuery(faultCase.code, faultCase.symptom,
                    faultCase.component, "2.0.0"));
            assertThat(result.success()).as(faultCase.toString()).isTrue();
            assertThat(result.evidence()).as(faultCase.toString()).isNotEmpty().hasSizeLessThanOrEqualTo(4);
            List<String> rankedDocuments = result.evidence().stream().map(item -> item.documentId()).toList();
            assertThat(rankedDocuments)
                    .as(faultCase.toString()).contains(faultCase.expectedDocument);
            if (rankedDocuments.stream().limit(2).anyMatch(faultCase.expectedDocument::equals)) {
                relevantInTopTwo++;
            }
        }
        assertThat(relevantInTopTwo).as("irrelevant documents must not dominate the calibrated cases")
                .isGreaterThanOrEqualTo(12);
    }

    private static Case c(String code, String symptom, String component, String expectedDocument) {
        return new Case(code, symptom, component, expectedDocument);
    }

    private record Case(String code, String symptom, String component, String expectedDocument) {
    }
}
