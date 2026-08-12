package com.yliu22520.iotota.knowledge;

public record KnowledgeEvidence(String evidenceId,
                                String documentId,
                                String documentVersion,
                                String section,
                                String chunkId,
                                String content,
                                double score) {

    public KnowledgeEvidence {
        if (!evidenceId.equals("knowledge:" + chunkId)) {
            throw new IllegalArgumentException("Knowledge evidence id must be derived from its stable chunk id");
        }
    }
}
