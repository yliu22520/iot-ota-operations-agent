package com.yliu22520.iotota.simulator;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageStateRepository extends JpaRepository<MessageState, Long> {
    List<MessageState> findByUpgradeTaskOrderByObservedAtAsc(UpgradeTask upgradeTask);
}
