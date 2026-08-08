package com.yliu22520.iotota.simulator;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FailureLogRepository extends JpaRepository<FailureLog, Long> {
    List<FailureLog> findByUpgradeTaskOrderByObservedAtAsc(UpgradeTask upgradeTask);
}
