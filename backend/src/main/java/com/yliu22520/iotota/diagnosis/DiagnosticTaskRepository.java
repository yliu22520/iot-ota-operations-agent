package com.yliu22520.iotota.diagnosis;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface DiagnosticTaskRepository extends JpaRepository<DiagnosticTask, UUID> {

    @Query("""
            select d from DiagnosticTask d
            where d.upgradeTask.id = :upgradeTaskId
              and d.state not in (com.yliu22520.iotota.diagnosis.DiagnosticState.COMPLETED,
                                  com.yliu22520.iotota.diagnosis.DiagnosticState.INCOMPLETE)
            """)
    @EntityGraph(attributePaths = {"upgradeTask", "upgradeTask.device", "upgradeTask.targetFirmwareVersion"})
    Optional<DiagnosticTask> findActiveByUpgradeTaskId(UUID upgradeTaskId);

    @Override
    @EntityGraph(attributePaths = {"upgradeTask", "upgradeTask.device", "upgradeTask.targetFirmwareVersion"})
    Optional<DiagnosticTask> findById(UUID id);
}
