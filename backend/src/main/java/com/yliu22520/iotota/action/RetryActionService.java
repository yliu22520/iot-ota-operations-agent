package com.yliu22520.iotota.action;

import com.yliu22520.iotota.simulator.SimulatorRetryGateway;
import com.yliu22520.iotota.simulator.SimulatorRetryResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RetryActionService {

    private final RetryAuthorizationTransaction authorization;
    private final RetryExecutionLedger ledger;
    private final RetryExecutionQueryService queryService;
    private final SimulatorRetryGateway simulatorRetryGateway;

    public RetryActionService(RetryAuthorizationTransaction authorization,
                              RetryExecutionLedger ledger,
                              RetryExecutionQueryService queryService,
                              SimulatorRetryGateway simulatorRetryGateway) {
        this.authorization = authorization;
        this.ledger = ledger;
        this.queryService = queryService;
        this.simulatorRetryGateway = simulatorRetryGateway;
    }

    public RetryExecutionView approveAndExecute(UUID diagnosticTaskId, int planVersion,
                                                boolean acknowledged, String actor) {
        RetryAuthorizationOutcome outcome = authorization.authorize(
                diagnosticTaskId, planVersion, acknowledged, actor);
        if (outcome.rejectionCode() != null) {
            throw new RetryApprovalRejectedException(outcome.rejectionCode());
        }
        RetryExecution execution = outcome.execution();
        if (!outcome.newExecution()) {
            return queryService.view(execution.getId(), true);
        }

        try {
            var snapshot = execution.getRetryPlan().getSnapshot();
            SimulatorRetryResult simulatorResult = simulatorRetryGateway.submit(diagnosticTaskId,
                    snapshot.upgradeTaskId(), snapshot.targetVersion(), execution.getIdempotencyKey());
            ledger.recordCallAccepted(execution.getId(), simulatorResult, actor);
            boolean verified = simulatorRetryGateway.findByIdempotencyKey(execution.getIdempotencyKey())
                    .filter(result -> result.attemptId().equals(simulatorResult.attemptId()))
                    .filter(result -> "ACCEPTED".equals(result.status()))
                    .isPresent();
            if (verified) {
                ledger.recordVerified(execution.getId(), actor);
            } else {
                ledger.recordIncomplete(execution.getId(), "SIMULATOR_FACT_NOT_FOUND", actor);
            }
        } catch (RuntimeException exception) {
            ledger.recordIncomplete(execution.getId(), "SIMULATOR_CALL_UNCONFIRMED", actor);
        }
        return queryService.view(execution.getId(), false);
    }
}
