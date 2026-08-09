package com.yliu22520.iotota.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public AuditEvent append(String actor, String objectType, String objectId, String action,
                             String result, String correlationId, String summary,
                             Map<String, Object> metadata) {
        return repository.save(new AuditEvent(UUID.randomUUID(), Instant.now(clock), actor,
                objectType, objectId, action, result, correlationId, summary, metadata));
    }
}
