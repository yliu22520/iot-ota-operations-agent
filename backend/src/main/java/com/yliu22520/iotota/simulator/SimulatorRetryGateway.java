package com.yliu22520.iotota.simulator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class SimulatorRetryGateway {

    private final SimulatorRetryAttemptRepository attemptRepository;
    private final UpgradeTaskRepository upgradeTaskRepository;
    private final Clock clock;

    public SimulatorRetryGateway(SimulatorRetryAttemptRepository attemptRepository,
                                 UpgradeTaskRepository upgradeTaskRepository,
                                 Clock clock) {
        this.attemptRepository = attemptRepository;
        this.upgradeTaskRepository = upgradeTaskRepository;
        this.clock = clock;
    }

    @Transactional
    public SimulatorRetryResult submit(UUID diagnosticTaskId, UUID sourceUpgradeTaskId,
                                       String targetVersion, String idempotencyKey) {
        Optional<SimulatorRetryAttempt> existing = attemptRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return toResult(existing.get(), true);
        }
        UpgradeTask task = upgradeTaskRepository.findById(sourceUpgradeTaskId)
                .orElseThrow(() -> new UpgradeTaskNotFoundException(sourceUpgradeTaskId));
        task.registerRetry();
        Instant now = Instant.now(clock);
        SimulatorRetryAttempt attempt = attemptRepository.save(new SimulatorRetryAttempt(
                UUID.randomUUID(), diagnosticTaskId, task, targetVersion, idempotencyKey, now));
        upgradeTaskRepository.save(task);
        return toResult(attempt, false);
    }

    @Transactional(readOnly = true)
    public Optional<SimulatorRetryResult> findByIdempotencyKey(String idempotencyKey) {
        return attemptRepository.findByIdempotencyKey(idempotencyKey).map(attempt -> toResult(attempt, true));
    }

    private SimulatorRetryResult toResult(SimulatorRetryAttempt attempt, boolean replay) {
        return new SimulatorRetryResult(attempt.getId(), attempt.getStatus(), replay, attempt.getCreatedAt());
    }
}
