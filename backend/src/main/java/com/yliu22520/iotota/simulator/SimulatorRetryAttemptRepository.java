package com.yliu22520.iotota.simulator;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SimulatorRetryAttemptRepository extends JpaRepository<SimulatorRetryAttempt, UUID> {

    Optional<SimulatorRetryAttempt> findByIdempotencyKey(String idempotencyKey);
}
