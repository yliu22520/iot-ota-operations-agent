package com.yliu22520.iotota.knowledge;

import java.util.List;
import java.util.Comparator;

public class KnowledgeSearchService implements KnowledgeRetriever {

    private final KnowledgeVectorRepository repository;
    private final KnowledgeSearchLimits limits;

    public KnowledgeSearchService(KnowledgeVectorRepository repository, KnowledgeSearchLimits limits) {
        this.repository = repository;
        this.limits = limits;
    }

    @Override
    public KnowledgeSearchResult search(KnowledgeSearchQuery query) {
        try {
            List<KnowledgeEvidence> evidence = repository.search(query.retrievalText(), 16,
                            limits.similarityThreshold()).stream()
                    .filter(hit -> "ACTIVE".equals(hit.status()))
                    .sorted(Comparator.comparingDouble(
                            (KnowledgeVectorHit hit) -> rankedScore(hit, query.component())).reversed())
                    .limit(limits.topK())
                    .map(this::toEvidence)
                    .toList();
            return KnowledgeSearchResult.success(evidence);
        } catch (RuntimeException exception) {
            return KnowledgeSearchResult.failure("KNOWLEDGE_RETRIEVAL_FAILED",
                    "The local knowledge source was unavailable.");
        }
    }

    private double rankedScore(KnowledgeVectorHit hit, String component) {
        boolean componentMatches = component != null && hit.component() != null
                && java.util.Arrays.stream(hit.component().split(","))
                .map(String::strip).anyMatch(component::equalsIgnoreCase);
        return hit.score() + (componentMatches ? 1.0 : 0.0);
    }

    private KnowledgeEvidence toEvidence(KnowledgeVectorHit hit) {
        String content = hit.content().length() <= limits.maxTextLength()
                ? hit.content()
                : hit.content().substring(0, limits.maxTextLength());
        return new KnowledgeEvidence("knowledge:" + hit.chunkId(),
                hit.documentId(), hit.documentVersion(), hit.section(), hit.chunkId(), content, hit.score());
    }
}
