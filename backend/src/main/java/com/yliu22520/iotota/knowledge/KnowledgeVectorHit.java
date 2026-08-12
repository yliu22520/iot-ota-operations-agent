package com.yliu22520.iotota.knowledge;

public record KnowledgeVectorHit(String documentId,
                                 String documentVersion,
                                 String status,
                                 String component,
                                 String section,
                                 String chunkId,
                                 String content,
                                 double score) {
}
