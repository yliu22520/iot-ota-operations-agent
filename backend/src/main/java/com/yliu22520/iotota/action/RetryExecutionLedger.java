package com.yliu22520.iotota.action;

import com.yliu22520.iotota.audit.AuditService;
import com.yliu22520.iotota.diagnosis.DiagnosticState;
import com.yliu22520.iotota.diagnosis.DiagnosticStateService;
import com.yliu22520.iotota.simulator.SimulatorRetryResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class RetryExecutionLedger {

    private final RetryExecutionRepository executionRepository;
    private final DiagnosticStateService stateService;
    private final AuditService auditService;
    private final Clock clock;

    public RetryExecutionLedger(RetryExecutionRepository executionRepository,
                                DiagnosticStateService stateService,
                                AuditService auditService,
                                Clock clock) {
        this.executionRepository = executionRepository;
        this.stateService = stateService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public void recordCallAccepted(UUID executionId, SimulatorRetryResult result, String actor) {
        RetryExecution execution = find(executionId);
        if (!"INTENT_RECORDED".equals(execution.getStatus())) {
            return;
        }
        Instant now = Instant.now(clock);
        execution.recordCallAccepted(result.attemptId(), result.acceptedAt(), now);
        UUID diagnosticTaskId = execution.getRetryPlan().getDiagnosticTask().getId();
        stateService.transition(diagnosticTaskId, DiagnosticState.VERIFYING, actor);
        auditService.append("system", "RETRY_EXECUTION", executionId.toString(),
                "SIMULATOR_CALL_RECORDED", "ACCEPTED", diagnosticTaskId.toString(),
                "Simulator call result was recorded in a new transaction",
                Map.of("simulatorAttemptId", result.attemptId().toString()));
    }

    @Transactional
    public void recordVerified(UUID executionId, String actor) {
        RetryExecution execution = find(executionId);
        if ("VERIFIED".equals(execution.getStatus())) {
            return;
        }
        Instant now = Instant.now(clock);
        execution.markVerified(now);
        execution.getRetryPlan().markExecuted();
        UUID diagnosticTaskId = execution.getRetryPlan().getDiagnosticTask().getId();
        stateService.transition(diagnosticTaskId, DiagnosticState.COMPLETED, actor);
        auditService.append("system", "RETRY_EXECUTION", executionId.toString(), "RETRY_VERIFIED",
                "BUSINESS_ACCEPTED", diagnosticTaskId.toString(),
                "Persistent task and simulator facts confirm the retry request was accepted",
                Map.of("verificationStatus", "BUSINESS_ACCEPTED"));
    }

    @Transactional
    public void recordIncomplete(UUID executionId, String reason, String actor) {
        RetryExecution execution = find(executionId);
        if ("VERIFIED".equals(execution.getStatus()) || "INCOMPLETE".equals(execution.getStatus())) {
            return;
        }
        execution.markIncomplete(reason, Instant.now(clock));
        UUID diagnosticTaskId = execution.getRetryPlan().getDiagnosticTask().getId();
        DiagnosticState state = execution.getRetryPlan().getDiagnosticTask().getState();
        if (state == DiagnosticState.EXECUTING || state == DiagnosticState.VERIFYING) {
            stateService.transition(diagnosticTaskId, DiagnosticState.INCOMPLETE, actor);
        }
        auditService.append("system", "RETRY_EXECUTION", executionId.toString(), "RETRY_VERIFICATION_INCOMPLETE",
                "INCOMPLETE", diagnosticTaskId.toString(),
                "Persistent facts cannot confirm whether the retry was accepted", Map.of("reason", reason));
    }

    private RetryExecution find(UUID executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Retry execution not found: " + executionId));
    }
}
