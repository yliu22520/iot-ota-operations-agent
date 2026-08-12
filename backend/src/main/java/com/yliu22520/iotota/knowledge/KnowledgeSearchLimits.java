package com.yliu22520.iotota.knowledge;

public record KnowledgeSearchLimits(int topK, int maxTextLength, double similarityThreshold) {

    public KnowledgeSearchLimits {
        if (topK < 1 || topK > 4) {
            throw new IllegalArgumentException("topK must be between 1 and 4");
        }
        if (maxTextLength < 1) {
            throw new IllegalArgumentException("maxTextLength must be positive");
        }
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("similarityThreshold must be between 0 and 1");
        }
    }
}
