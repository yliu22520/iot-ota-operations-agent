package com.yliu22520.iotota.diagnosis;

import java.time.Instant;

/** A stable, user-visible pointer to one observed fact. */
public record EvidenceRef(String evidenceId,
                          String source,
                          Instant observedAt,
                          String summary,
                          String kind,
                          String locator) {

    public EvidenceRef(String evidenceId, String source, Instant observedAt, String summary) {
        this(evidenceId, source, observedAt, summary, kindOf(evidenceId), locatorOf(evidenceId));
    }

    private static String kindOf(String evidenceId) {
        if (evidenceId == null) {
            return "UNKNOWN";
        }
        if (evidenceId.startsWith("upgrade-task:")) {
            return "TASK";
        }
        if (evidenceId.startsWith("device:")) {
            return "DEVICE";
        }
        if (evidenceId.startsWith("firmware:")) {
            return "FIRMWARE_VERSION";
        }
        if (evidenceId.startsWith("failure-logs:")) {
            return "FAILURE_LOG";
        }
        if (evidenceId.startsWith("message-state:")) {
            return "MESSAGE_STATE";
        }
        if (evidenceId.startsWith("knowledge:")) {
            return "KNOWLEDGE";
        }
        if (evidenceId.startsWith("version-compatibility:")) {
            return "RULE";
        }
        if (evidenceId.startsWith("knowledge-search:")) {
            return "KNOWLEDGE_SEARCH";
        }
        return "OTHER";
    }

    private static String locatorOf(String evidenceId) {
        if (evidenceId == null) {
            return "";
        }
        int separator = evidenceId.indexOf(':');
        return separator < 0 ? evidenceId : evidenceId.substring(separator + 1);
    }
}
