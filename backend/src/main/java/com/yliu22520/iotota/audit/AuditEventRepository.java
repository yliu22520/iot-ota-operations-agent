package com.yliu22520.iotota.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByObjectTypeAndObjectIdOrderByOccurredAtAsc(String objectType, String objectId);

    List<AuditEvent> findByCorrelationIdOrderByOccurredAtAsc(String correlationId);
}
