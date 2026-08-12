package com.yliu22520.iotota.action;

import com.yliu22520.iotota.diagnosis.DiagnosticReportDocument;
import com.yliu22520.iotota.diagnosis.DiagnosticTask;
import com.yliu22520.iotota.diagnosis.DiagnosticTaskNotFoundException;
import com.yliu22520.iotota.diagnosis.DiagnosticTaskRepository;
import com.yliu22520.iotota.diagnosis.RetryPlanDocument;
import com.yliu22520.iotota.diagnosis.RetryPlanPort;
import com.yliu22520.iotota.diagnosis.RetryPlanRequest;
import com.yliu22520.iotota.diagnosis.RetryPlanningResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DatabaseRetryPlanAdapter implements RetryPlanPort {

    private static final Duration PLAN_LIFETIME = Duration.ofMinutes(15);

    private final RetryEligibilityRule eligibilityRule;
    private final RetryPlanRepository retryPlanRepository;
    private final DiagnosticTaskRepository diagnosticTaskRepository;
    private final Clock clock;

    public DatabaseRetryPlanAdapter(RetryEligibilityRule eligibilityRule,
                                    RetryPlanRepository retryPlanRepository,
                                    DiagnosticTaskRepository diagnosticTaskRepository,
                                    Clock clock) {
        this.eligibilityRule = eligibilityRule;
        this.retryPlanRepository = retryPlanRepository;
        this.diagnosticTaskRepository = diagnosticTaskRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RetryPlanningResult evaluateAndCreate(RetryPlanRequest request) {
        RetryEligibilityDecision decision = eligibilityRule.evaluate(new RetryEligibilityFacts(
                request.upgradeTaskId(), request.taskVersion(), request.taskStatus(), request.failureCode(),
                request.deviceOnline(), request.versionCompatible(), request.retryCount(), request.maxRetries(),
                request.affectedTaskCount(), request.messageSendStatus(), request.callbackStatus()));
        String reasonCode = decision.reasonCodes().get(0);
        DiagnosticReportDocument.RetryEligibility eligibility = new DiagnosticReportDocument.RetryEligibility(
                decision.eligible(), decision.status(), reasonCode,
                decision.eligible() ? "Backend rules permit one approved retry." : "Backend rules forbid retry: " + reasonCode);
        if (!decision.eligible()) {
            return new RetryPlanningResult(eligibility, null);
        }

        DiagnosticTask task = diagnosticTaskRepository.findById(request.diagnosticTaskId())
                .orElseThrow(() -> new DiagnosticTaskNotFoundException(request.diagnosticTaskId()));
        Instant now = Instant.now(clock);
        UUID planId = UUID.randomUUID();
        RetryPlanDocument snapshot = new RetryPlanDocument(planId, 1, request.diagnosticTaskId(),
                request.upgradeTaskId(), request.taskVersion(), request.taskStatus(), request.targetVersion(), request.failureCode(),
                request.retryCount(), request.maxRetries(), request.deviceOnline(), request.versionCompatible(),
                request.messageSendStatus(), request.callbackStatus(), "SINGLE_UPGRADE_TASK",
                List.of("TASK_FINAL_FAILURE", "DEVICE_ONLINE", "VERSION_COMPATIBLE", "CALLBACK_TIMEOUT_CONFIRMED",
                        "RETRY_LIMIT_AVAILABLE"), request.evidenceRefs(), "PENDING_APPROVAL", now,
                now.plus(PLAN_LIFETIME));
        retryPlanRepository.save(new RetryPlan(planId, task, 1, "PENDING_APPROVAL", snapshot.expiresAt(), snapshot, now));
        return new RetryPlanningResult(eligibility, snapshot);
    }
}
