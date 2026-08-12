package com.yliu22520.iotota.knowledge;

import java.util.List;

public record KnowledgeSearchResult(boolean success,
                                    List<KnowledgeEvidence> evidence,
                                    String errorCode,
                                    String error) {

    public static KnowledgeSearchResult success(List<KnowledgeEvidence> evidence) {
        return new KnowledgeSearchResult(true, List.copyOf(evidence), null, null);
    }

    public static KnowledgeSearchResult failure(String errorCode, String error) {
        return new KnowledgeSearchResult(false, List.of(), errorCode, error);
    }
}
