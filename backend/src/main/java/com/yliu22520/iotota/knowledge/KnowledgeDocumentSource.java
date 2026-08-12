package com.yliu22520.iotota.knowledge;

import java.time.Instant;
import java.util.List;

public record KnowledgeDocumentSource(String documentId,
                                      String documentVersion,
                                      String documentType,
                                      String status,
                                      List<String> applicableComponents,
                                      Instant updatedAt,
                                      String content,
                                      String contentHash,
                                      List<KnowledgeChunkSource> chunks) {
}
