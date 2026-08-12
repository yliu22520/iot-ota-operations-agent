package com.yliu22520.iotota.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class KnowledgeIndexInitializer {

    private final KnowledgeDocumentCatalog catalog;
    private final JdbcTemplate jdbcTemplate;
    private final VectorStore vectorStore;

    public KnowledgeIndexInitializer(KnowledgeDocumentCatalog catalog,
                                     JdbcTemplate jdbcTemplate,
                                     VectorStore vectorStore) {
        this.catalog = catalog;
        this.jdbcTemplate = jdbcTemplate;
        this.vectorStore = vectorStore;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initialize() {
        List<KnowledgeDocumentSource> documents = catalog.loadActiveDocuments();
        List<Document> vectorDocuments = new ArrayList<>();
        Map<String, String> indexedHashes = jdbcTemplate.query(
                "select metadata ->> 'chunkId', metadata ->> 'contentHash' from knowledge_chunk",
                resultSet -> {
                    Map<String, String> hashes = new java.util.HashMap<>();
                    while (resultSet.next()) {
                        hashes.put(resultSet.getString(1), resultSet.getString(2));
                    }
                    return hashes;
                });
        Set<String> activeChunkIds = new java.util.HashSet<>();
        for (KnowledgeDocumentSource document : documents) {
            upsertDocument(document);
            for (KnowledgeChunkSource chunk : document.chunks()) {
                activeChunkIds.add(chunk.chunkId());
                if (!document.contentHash().equals(indexedHashes.get(chunk.chunkId()))) {
                    vectorDocuments.add(toVectorDocument(document, chunk));
                }
            }
        }
        deleteInactiveChunks(activeChunkIds);
        deleteInactiveDocuments(documents.stream().map(KnowledgeDocumentSource::documentId).collect(
                java.util.stream.Collectors.toSet()));
        if (!vectorDocuments.isEmpty()) {
            vectorStore.add(vectorDocuments);
        }
    }

    private void deleteInactiveChunks(Set<String> activeChunkIds) {
        List<String> staleVectorIds = jdbcTemplate.query(
                "select id::text, metadata ->> 'chunkId' from knowledge_chunk",
                (resultSet, row) -> activeChunkIds.contains(resultSet.getString(2)) ? null : resultSet.getString(1))
                .stream().filter(java.util.Objects::nonNull).toList();
        if (!staleVectorIds.isEmpty()) {
            vectorStore.delete(staleVectorIds);
        }
    }

    private void deleteInactiveDocuments(Set<String> activeDocumentIds) {
        jdbcTemplate.queryForList("select id from knowledge_document", String.class).stream()
                .filter(documentId -> !activeDocumentIds.contains(documentId))
                .forEach(documentId -> jdbcTemplate.update("delete from knowledge_document where id = ?", documentId));
    }

    private void upsertDocument(KnowledgeDocumentSource document) {
        jdbcTemplate.update("""
                insert into knowledge_document(id, document_version, document_type, status,
                                               applicable_components, updated_at, content, content_hash)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set
                    document_version = excluded.document_version,
                    document_type = excluded.document_type,
                    status = excluded.status,
                    applicable_components = excluded.applicable_components,
                    updated_at = excluded.updated_at,
                    content = excluded.content,
                    content_hash = excluded.content_hash
                """, document.documentId(), document.documentVersion(), document.documentType(), document.status(),
                String.join(",", document.applicableComponents()), Timestamp.from(document.updatedAt()), document.content(),
                document.contentHash());
    }

    private Document toVectorDocument(KnowledgeDocumentSource source, KnowledgeChunkSource chunk) {
        String stableUuid = UUID.nameUUIDFromBytes(chunk.chunkId().getBytes(StandardCharsets.UTF_8)).toString();
        Map<String, Object> metadata = Map.of(
                "documentId", source.documentId(),
                "documentVersion", source.documentVersion(),
                "documentType", source.documentType(),
                "status", source.status(),
                "component", String.join(",", source.applicableComponents()),
                "section", chunk.section(),
                "chunkId", chunk.chunkId(),
                "contentHash", source.contentHash());
        return new Document(stableUuid, chunk.content(), metadata);
    }
}
