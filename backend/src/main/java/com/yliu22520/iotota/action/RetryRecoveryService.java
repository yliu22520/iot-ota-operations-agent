package com.yliu22520.iotota.action;

import com.yliu22520.iotota.simulator.SimulatorRetryGateway;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetryRecoveryService {

    private final RetryExecutionRepository executionRepository;
    private final SimulatorRetryGateway simulatorRetryGateway;
    private final RetryExecutionLedger ledger;

    public RetryRecoveryService(RetryExecutionRepository executionRepository,
                                SimulatorRetryGateway simulatorRetryGateway,
                                RetryExecutionLedger ledger) {
        this.executionRepository = executionRepository;
        this.simulatorRetryGateway = simulatorRetryGateway;
        this.ledger = ledger;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileAfterStartup() {
        reconcilePending();
    }

    public void reconcilePending() {
        List<RetryExecution> pending = executionRepository.findByStatusIn(
                List.of("INTENT_RECORDED", "CALL_ACCEPTED"));
        for (RetryExecution execution : pending) {
            var fact = simulatorRetryGateway.findByIdempotencyKey(execution.getIdempotencyKey());
            if (fact.isEmpty()) {
                ledger.recordIncomplete(execution.getId(), "RECOVERY_FACT_NOT_FOUND", "system-recovery");
                continue;
            }
            if ("INTENT_RECORDED".equals(execution.getStatus())) {
                ledger.recordCallAccepted(execution.getId(), fact.get(), "system-recovery");
            }
            ledger.recordVerified(execution.getId(), "system-recovery");
        }
    }
}
