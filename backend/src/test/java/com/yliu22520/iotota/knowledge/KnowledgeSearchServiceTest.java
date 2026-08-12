package com.yliu22520.iotota.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeSearchServiceTest {

    @Test
    void limitsKnowledgeEvidenceCountAndTextLengthAtThePublicSearchSeam() {
        KnowledgeVectorRepository repository = (query, topK, similarityThreshold) -> List.of(
                hit("doc-1", "chunk-1", "A".repeat(900), 0.99),
                hit("doc-2", "chunk-2", "B".repeat(900), 0.98),
                hit("doc-3", "chunk-3", "C".repeat(900), 0.97),
                hit("doc-4", "chunk-4", "D".repeat(900), 0.96),
                hit("doc-5", "chunk-5", "E".repeat(900), 0.95),
                new KnowledgeVectorHit("retired-doc", "1.0", "RETIRED", "firmware",
                        "Old", "retired-chunk", "obsolete advice", 1.0));
        KnowledgeSearchService service = new KnowledgeSearchService(repository,
                new KnowledgeSearchLimits(4, 600, 0.70));

        KnowledgeSearchResult result = service.search(new KnowledgeSearchQuery(
                "VERSION_INCOMPATIBLE", "target model is unsupported", "firmware", "2.0.0"));

        assertThat(result.success()).isTrue();
        assertThat(result.evidence()).hasSize(4);
        assertThat(result.evidence()).extracting(KnowledgeEvidence::documentId).doesNotContain("retired-doc");
        assertThat(result.evidence()).allSatisfy(item -> {
            assertThat(item.content()).hasSizeLessThanOrEqualTo(600);
            assertThat(item.evidenceId()).startsWith("knowledge:");
        });
    }

    @Test
    void convertsRetrievalFailureIntoAnExplicitEvidenceGap() {
        KnowledgeVectorRepository repository = (query, topK, similarityThreshold) -> {
            throw new IllegalStateException("pgvector unavailable");
        };
        KnowledgeSearchService service = new KnowledgeSearchService(repository,
                new KnowledgeSearchLimits(4, 600, 0.70));

        KnowledgeSearchResult result = service.search(new KnowledgeSearchQuery(
                "CALLBACK_TIMEOUT", "callback missing", "message", "2.0.0"));

        assertThat(result.success()).isFalse();
        assertThat(result.evidence()).isEmpty();
        assertThat(result.errorCode()).isEqualTo("KNOWLEDGE_RETRIEVAL_FAILED");
        assertThat(result.error()).doesNotContain("pgvector unavailable");
    }

    @Test
    void rejectsAnEvidenceReferenceThatIsNotDerivedFromItsChunk() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new KnowledgeEvidence(
                        "knowledge:forged", "doc-1", "1.0", "Diagnosis", "doc-1:diagnosis:001",
                        "advice", 0.9))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void metadataComponentMatchPreventsIrrelevantHighScoreDocumentsFromDominating() {
        KnowledgeVectorRepository repository = (query, topK, threshold) -> List.of(
                new KnowledgeVectorHit("irrelevant", "1.0", "ACTIVE", "firmware", "Version", "i:001",
                        "version advice", 0.99),
                new KnowledgeVectorHit("retry", "1.0", "ACTIVE", "retry,diagnosis", "Safety", "r:001",
                        "approval advice", 0.72));
        KnowledgeSearchService service = new KnowledgeSearchService(repository,
                new KnowledgeSearchLimits(4, 600, 0.70));

        var result = service.search(new KnowledgeSearchQuery("RETRY_REJECTED", "approval required", "retry", "2"));

        assertThat(result.evidence()).extracting(KnowledgeEvidence::documentId)
                .containsExactly("retry", "irrelevant");
    }

    private KnowledgeVectorHit hit(String documentId, String chunkId, String content, double score) {
        return new KnowledgeVectorHit(documentId, "1.0", "ACTIVE", "firmware",
                "Diagnosis", chunkId, content, score);
    }
}
