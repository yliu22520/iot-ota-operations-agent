package com.yliu22520.iotota.knowledge;

/** Structured search fields; arbitrary model-authored search prompts are not accepted. */
public record KnowledgeSearchQuery(String failureCode,
                                   String symptom,
                                   String component,
                                   String targetVersion) {

    public String retrievalText() {
        return "query: OTA failure " + safe(failureCode) + " symptom " + safe(symptom)
                + " component " + safe(component) + " target version " + safe(targetVersion);
    }

    private static String safe(String value) {
        return value == null ? "unknown" : value.strip();
    }
}
