package com.yliu22520.iotota.knowledge;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class KnowledgeDocumentCatalog {

    private static final String RESOURCE_PATTERN = "classpath*:knowledge/*.md";

    private final ResourcePatternResolver resolver;
    private final KnowledgeChunker chunker = new KnowledgeChunker();

    public KnowledgeDocumentCatalog(ResourcePatternResolver resolver) {
        this.resolver = resolver;
    }

    public List<KnowledgeDocumentSource> loadActiveDocuments() {
        try {
            return Arrays.stream(resolver.getResources(RESOURCE_PATTERN))
                    .map(this::parse)
                    .filter(document -> "ACTIVE".equals(document.status()))
                    .sorted(java.util.Comparator.comparing(KnowledgeDocumentSource::documentId))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read the local knowledge catalogue", exception);
        }
    }

    private KnowledgeDocumentSource parse(Resource resource) {
        try {
            String markdown = resource.getContentAsString(StandardCharsets.UTF_8);
            String[] parts = markdown.split("(?m)^---\\s*$", 3);
            if (parts.length != 3 || !parts[0].isBlank()) {
                throw new IllegalArgumentException("Knowledge document requires YAML-style front matter: "
                        + resource.getFilename());
            }
            Map<String, String> metadata = parseMetadata(parts[1]);
            String content = parts[2].strip();
            String documentId = required(metadata, "documentId", resource);
            List<KnowledgeChunkSource> chunks = splitIntoSections(documentId, content);
            return new KnowledgeDocumentSource(documentId,
                    required(metadata, "version", resource),
                    required(metadata, "type", resource),
                    required(metadata, "status", resource),
                    splitCsv(required(metadata, "applicableComponents", resource)),
                    Instant.parse(required(metadata, "updatedAt", resource)),
                    content, sha256(content), chunks);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read " + resource.getFilename(), exception);
        }
    }

    private Map<String, String> parseMetadata(String frontMatter) {
        Map<String, String> metadata = new LinkedHashMap<>();
        frontMatter.lines().filter(line -> !line.isBlank()).forEach(line -> {
            int separator = line.indexOf(':');
            if (separator <= 0) {
                throw new IllegalArgumentException("Invalid knowledge front matter line: " + line);
            }
            metadata.put(line.substring(0, separator).strip(), line.substring(separator + 1).strip());
        });
        return metadata;
    }

    private List<KnowledgeChunkSource> splitIntoSections(String documentId, String content) {
        List<KnowledgeChunkSource> chunks = new ArrayList<>();
        String currentSection = "Overview";
        StringBuilder currentContent = new StringBuilder();
        for (String line : content.split("\\R")) {
            if (line.startsWith("#")) {
                addChunk(chunks, documentId, currentSection, currentContent.toString());
                currentSection = line.replaceFirst("^#+\\s*", "").strip();
                currentContent = new StringBuilder();
            } else {
                currentContent.append(line).append('\n');
            }
        }
        addChunk(chunks, documentId, currentSection, currentContent.toString());
        return List.copyOf(chunks);
    }

    private void addChunk(List<KnowledgeChunkSource> chunks, String documentId, String section, String text) {
        String body = text.strip();
        if (body.isBlank()) {
            return;
        }
        List<String> windows = chunker.split(body);
        for (String window : windows) {
            String chunkId = documentId + ":" + slug(section) + ":"
                    + String.format(Locale.ROOT, "%03d", chunks.size() + 1);
            chunks.add(new KnowledgeChunkSource(chunkId, section, "passage: " + section + "\n" + window));
        }
    }

    private String required(Map<String, String> metadata, String key, Resource resource) {
        String value = metadata.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + key + " in " + resource.getFilename());
        }
        return value;
    }

    private List<String> splitCsv(String value) {
        return Arrays.stream(value.split(",")).map(String::strip).filter(item -> !item.isBlank()).toList();
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
