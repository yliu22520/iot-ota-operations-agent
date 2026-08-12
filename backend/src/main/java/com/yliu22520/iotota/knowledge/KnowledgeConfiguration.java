package com.yliu22520.iotota.knowledge;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

@Configuration
public class KnowledgeConfiguration {

    @Bean
    @ConditionalOnMissingBean(EmbeddingModel.class)
    @ConditionalOnProperty(name = "knowledge.embedding.provider", havingValue = "onnx", matchIfMissing = true)
    TransformersEmbeddingModel localOnnxEmbeddingModel(
            ResourceLoader resourceLoader,
            @Value("${knowledge.embedding.model-uri}") String modelUri,
            @Value("${knowledge.embedding.tokenizer-uri}") String tokenizerUri,
            @Value("${knowledge.embedding.cache-directory:${java.io.tmpdir}/iot-ota-onnx}") String cacheDirectory) {
        TransformersEmbeddingModel model = new TransformersEmbeddingModel();
        model.setModelResource(resourceLoader.getResource(modelUri));
        model.setTokenizerResource(resourceLoader.getResource(tokenizerUri));
        model.setResourceCacheDirectory(cacheDirectory);
        model.setTokenizerOptions(Map.of("padding", "true", "truncation", "true"));
        return model;
    }

    @Bean
    PgVectorStore knowledgeVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
                                       @Value("${knowledge.embedding.dimensions:384}") int dimensions) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .schemaName("public")
                .vectorTableName("knowledge_chunk")
                .dimensions(dimensions)
                .distanceType(COSINE_DISTANCE)
                .indexType(HNSW)
                .initializeSchema(false)
                .vectorTableValidationsEnabled(false)
                .maxDocumentBatchSize(64)
                .build();
    }

    @Bean
    KnowledgeDocumentCatalog knowledgeDocumentCatalog() {
        return new KnowledgeDocumentCatalog(new PathMatchingResourcePatternResolver());
    }

    @Bean
    KnowledgeVectorRepository knowledgeVectorRepository(VectorStore knowledgeVectorStore) {
        return new SpringAiKnowledgeVectorRepository(knowledgeVectorStore);
    }

    @Bean
    KnowledgeRetriever knowledgeRetriever(KnowledgeVectorRepository repository,
                                           @Value("${knowledge.search.top-k:4}") int topK,
                                           @Value("${knowledge.search.max-text-length:600}") int maxTextLength,
                                           @Value("${knowledge.search.similarity-threshold:0.70}") double threshold) {
        return new KnowledgeSearchService(repository, new KnowledgeSearchLimits(topK, maxTextLength, threshold));
    }

    @Bean
    KnowledgeIndexInitializer knowledgeIndexInitializer(KnowledgeDocumentCatalog catalog,
                                                        JdbcTemplate jdbcTemplate,
                                                        VectorStore knowledgeVectorStore) {
        return new KnowledgeIndexInitializer(catalog, jdbcTemplate, knowledgeVectorStore);
    }
}
