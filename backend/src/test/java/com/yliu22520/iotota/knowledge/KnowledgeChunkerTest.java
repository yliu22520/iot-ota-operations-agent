package com.yliu22520.iotota.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeChunkerTest {

    @Test
    void createsBoundedWindowsWithFiftyApproximateTokensOfOverlap() {
        KnowledgeChunker chunker = new KnowledgeChunker();
        String content = "诊".repeat(950);

        var chunks = chunker.split(content);

        assertThat(chunks).hasSize(2);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunker.approximateTokenCount(chunk)).isBetween(300, 500));
        assertThat(chunks.get(0).substring(450)).isEqualTo(chunks.get(1).substring(0, 50));
    }
}
