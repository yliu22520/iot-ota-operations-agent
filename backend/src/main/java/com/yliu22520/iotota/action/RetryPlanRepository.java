package com.yliu22520.iotota.action;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface RetryPlanRepository extends JpaRepository<RetryPlan, UUID> {

    Optional<RetryPlan> findByDiagnosticTaskIdAndPlanVersion(UUID diagnosticTaskId, int planVersion);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RetryPlan p where p.diagnosticTask.id = :diagnosticTaskId and p.planVersion = :planVersion")
    Optional<RetryPlan> findLocked(@Param("diagnosticTaskId") UUID diagnosticTaskId,
                                   @Param("planVersion") int planVersion);
}
