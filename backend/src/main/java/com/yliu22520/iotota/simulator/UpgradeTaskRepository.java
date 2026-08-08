package com.yliu22520.iotota.simulator;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UpgradeTaskRepository extends JpaRepository<UpgradeTask, UUID> {

    @EntityGraph(attributePaths = {"device", "targetFirmwareVersion"})
    List<UpgradeTask> findByStatusOrderByFailedAtDesc(UpgradeTaskStatus status);

    @Override
    @EntityGraph(attributePaths = {"device", "targetFirmwareVersion"})
    java.util.Optional<UpgradeTask> findById(UUID id);
}
