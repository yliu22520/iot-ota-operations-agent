package com.yliu22520.iotota.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeDocumentCatalogTest {

    @Test
    void loadsEightActiveOtaDocumentsWithStableDocumentAndChunkIdentifiers() {
        KnowledgeDocumentCatalog catalog = new KnowledgeDocumentCatalog(
                new PathMatchingResourcePatternResolver());

        var documents = catalog.loadActiveDocuments();

        assertThat(documents).hasSize(8);
        assertThat(documents).extracting(KnowledgeDocumentSource::documentId).doesNotHaveDuplicates();
        assertThat(documents).allSatisfy(document -> {
            assertThat(document.status()).isEqualTo("ACTIVE");
            assertThat(document.contentHash()).matches("[0-9a-f]{64}");
            assertThat(document.chunks()).isNotEmpty();
            assertThat(document.chunks()).allSatisfy(chunk -> {
                assertThat(chunk.chunkId()).startsWith(document.documentId() + ":");
                assertThat(chunk.section()).isNotBlank();
                assertThat(chunk.content()).isNotBlank();
            });
        });
    }
}
