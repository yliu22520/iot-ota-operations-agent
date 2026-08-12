package com.yliu22520.iotota.action;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetryExecutionRepository extends JpaRepository<RetryExecution, UUID> {

    Optional<RetryExecution> findByRetryPlanId(UUID retryPlanId);

    List<RetryExecution> findByStatusIn(Collection<String> statuses);
}
