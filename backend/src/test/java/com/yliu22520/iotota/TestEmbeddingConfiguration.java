package com.yliu22520.iotota;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@TestConfiguration
public class TestEmbeddingConfiguration {

    @Bean
    @Primary
    EmbeddingModel deterministicEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                List<Embedding> embeddings = new ArrayList<>();
                for (int index = 0; index < request.getInstructions().size(); index++) {
                    embeddings.add(new Embedding(vector(request.getInstructions().get(index)), index));
                }
                return new EmbeddingResponse(embeddings);
            }

            @Override
            public float[] embed(Document document) {
                return vector(document.getText());
            }

            @Override
            public int dimensions() {
                return 384;
            }

            private float[] vector(String input) {
                String text = input.toLowerCase(Locale.ROOT);
                float[] vector = new float[384];
                feature(vector, 0, text, "version_incompatible", "unsupported", "compatible", "firmware", "型号", "兼容");
                feature(vector, 1, text, "callback_timeout", "callback", "message", "回调");
                feature(vector, 2, text, "checksum", "download", "校验");
                feature(vector, 3, text, "offline", "storage", "离线", "存储");
                feature(vector, 4, text, "retry", "重试", "审批");
                vector[383] = 0.05f;
                return vector;
            }

            private void feature(float[] vector, int index, String text, String... keywords) {
                for (String keyword : keywords) {
                    if (text.contains(keyword)) {
                        vector[index] += 1.0f;
                    }
                }
            }
        };
    }
}
