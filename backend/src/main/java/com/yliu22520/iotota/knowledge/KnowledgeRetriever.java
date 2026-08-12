package com.yliu22520.iotota.knowledge;

@FunctionalInterface
public interface KnowledgeRetriever {

    KnowledgeSearchResult search(KnowledgeSearchQuery query);
}
