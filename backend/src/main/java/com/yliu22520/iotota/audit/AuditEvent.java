package com.yliu22520.iotota.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private String actor;

    @Column(name = "object_type", nullable = false)
    private String objectType;

    @Column(name = "object_id", nullable = false)
    private String objectId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String result;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(nullable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    protected AuditEvent() {
    }

    public AuditEvent(UUID id, Instant occurredAt, String actor, String objectType, String objectId,
                      String action, String result, String correlationId, String summary,
                      Map<String, Object> metadata) {
        this.id = id;
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.objectType = objectType;
        this.objectId = objectId;
        this.action = action;
        this.result = result;
        this.correlationId = correlationId;
        this.summary = summary;
        this.metadata = metadata;
    }

    public UUID getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActor() { return actor; }
    public String getObjectType() { return objectType; }
    public String getObjectId() { return objectId; }
    public String getAction() { return action; }
    public String getResult() { return result; }
    public String getCorrelationId() { return correlationId; }
    public String getSummary() { return summary; }
    public Map<String, Object> getMetadata() { return metadata; }
}
