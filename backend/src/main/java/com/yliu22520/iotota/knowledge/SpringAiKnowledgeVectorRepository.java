package com.yliu22520.iotota.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

public class SpringAiKnowledgeVectorRepository implements KnowledgeVectorRepository {

    private final VectorStore vectorStore;

    public SpringAiKnowledgeVectorRepository(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<KnowledgeVectorHit> search(String query, int topK, double similarityThreshold) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .filterExpression("status == 'ACTIVE'")
                .build();
        return vectorStore.similaritySearch(request).stream().map(this::toHit).toList();
    }

    private KnowledgeVectorHit toHit(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new KnowledgeVectorHit(text(metadata, "documentId"), text(metadata, "documentVersion"),
                text(metadata, "status"), text(metadata, "component"), text(metadata, "section"),
                text(metadata, "chunkId"), document.getText(),
                document.getScore() == null ? 0.0 : document.getScore());
    }

    private String text(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            throw new IllegalStateException("Knowledge vector is missing metadata: " + key);
        }
        return value.toString();
    }
}
