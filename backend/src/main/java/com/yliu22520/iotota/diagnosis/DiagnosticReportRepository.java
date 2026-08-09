package com.yliu22520.iotota.diagnosis;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DiagnosticReportRepository extends JpaRepository<DiagnosticReport, UUID> {

    @EntityGraph(attributePaths = {"diagnosticTask"})
    Optional<DiagnosticReport> findByDiagnosticTaskId(UUID diagnosticTaskId);
}
