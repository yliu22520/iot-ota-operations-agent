package com.yliu22520.iotota;

import com.yliu22520.iotota.knowledge.KnowledgeRetriever;
import com.yliu22520.iotota.knowledge.KnowledgeIndexInitializer;
import com.yliu22520.iotota.knowledge.KnowledgeSearchQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Import(TestEmbeddingConfiguration.class)
class KnowledgeRetrievalIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("ota_operations")
            .withUsername("ota")
            .withPassword("ota");

    @Autowired
    private KnowledgeRetriever knowledgeRetriever;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KnowledgeIndexInitializer initializer;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("knowledge.embedding.provider", () -> "test");
    }

    @Test
    void indexesEightDocumentsInPgvectorAndRetrievesRelevantEvidence() {
        var result = knowledgeRetriever.search(new KnowledgeSearchQuery(
                "VERSION_INCOMPATIBLE", "target model is unsupported", "firmware", "2.0.0"));

        assertThat(jdbcTemplate.queryForObject("select count(*) from knowledge_document", Integer.class))
                .isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("select count(*) from knowledge_chunk", Integer.class))
                .isGreaterThanOrEqualTo(8);
        assertThat(jdbcTemplate.queryForObject(
                "select min(vector_dims(embedding)) from knowledge_chunk", Integer.class)).isEqualTo(384);
        assertThat(result.success()).isTrue();
        assertThat(result.evidence()).isNotEmpty().hasSizeLessThanOrEqualTo(4);
        assertThat(result.evidence().get(0).documentId())
                .isIn("ota-version-compatibility", "case-incompatible-device-model");
        assertThat(result.evidence()).allSatisfy(evidence -> {
            assertThat(evidence.evidenceId()).isEqualTo("knowledge:" + evidence.chunkId());
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from knowledge_chunk where metadata ->> 'chunkId' = ?",
                    Integer.class, evidence.chunkId())).isEqualTo(1);
        });

        int chunkCount = jdbcTemplate.queryForObject("select count(*) from knowledge_chunk", Integer.class);
        initializer.initialize();
        assertThat(jdbcTemplate.queryForObject("select count(*) from knowledge_chunk", Integer.class))
                .isEqualTo(chunkCount);
    }

    @Test
    void representativeFaultCasesKeepRelevantDocumentsAheadOfIrrelevantOnes() {
        assertRelevant("VERSION_INCOMPATIBLE", "unsupported device model", "firmware",
                "ota-version-compatibility", "case-incompatible-device-model");
        assertRelevant("CALLBACK_TIMEOUT", "callback missing after message sent", "message",
                "ota-callback-timeout", "case-late-device-callback");
        assertRelevant("CHECKSUM_MISMATCH", "download checksum verification failed", "download",
                "ota-download-checksum");
        assertRelevant("DEVICE_OFFLINE", "device offline and storage low", "device",
                "ota-device-offline-resources");
        assertRelevant("RETRY_REJECTED", "retry requires approval", "retry",
                "ota-retry-safety");
    }

    private void assertRelevant(String failureCode, String symptom, String component, String... expectedDocuments) {
        var result = knowledgeRetriever.search(new KnowledgeSearchQuery(failureCode, symptom, component, "2.0.0"));
        assertThat(result.success()).isTrue();
        assertThat(result.evidence()).isNotEmpty().hasSizeLessThanOrEqualTo(4);
        assertThat(result.evidence().stream().limit(2).map(evidence -> evidence.documentId()).toList())
                .containsAnyOf(expectedDocuments);
    }
}
