package com.yliu22520.iotota.knowledge;

import java.util.List;

@FunctionalInterface
public interface KnowledgeVectorRepository {

    List<KnowledgeVectorHit> search(String query, int topK, double similarityThreshold);
}
