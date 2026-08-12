package com.yliu22520.iotota.action;

import com.yliu22520.iotota.audit.AuditService;
import com.yliu22520.iotota.diagnosis.DiagnosticState;
import com.yliu22520.iotota.diagnosis.DiagnosticStateService;
import com.yliu22520.iotota.diagnosis.VersionCompatibilityDecision;
import com.yliu22520.iotota.diagnosis.VersionCompatibilityFacts;
import com.yliu22520.iotota.diagnosis.VersionCompatibilityRule;
import com.yliu22520.iotota.simulator.MessageState;
import com.yliu22520.iotota.simulator.MessageStateRepository;
import com.yliu22520.iotota.simulator.UpgradeTask;
import com.yliu22520.iotota.simulator.UpgradeTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RetryAuthorizationTransaction {

    private final RetryPlanRepository planRepository;
    private final RetryExecutionRepository executionRepository;
    private final UpgradeTaskRepository upgradeTaskRepository;
    private final MessageStateRepository messageStateRepository;
    private final RetryEligibilityRule eligibilityRule;
    private final VersionCompatibilityRule compatibilityRule;
    private final DiagnosticStateService stateService;
    private final AuditService auditService;
    private final Clock clock;

    public RetryAuthorizationTransaction(RetryPlanRepository planRepository,
                                         RetryExecutionRepository executionRepository,
                                         UpgradeTaskRepository upgradeTaskRepository,
                                         MessageStateRepository messageStateRepository,
                                         RetryEligibilityRule eligibilityRule,
                                         VersionCompatibilityRule compatibilityRule,
                                         DiagnosticStateService stateService,
                                         AuditService auditService,
                                         Clock clock) {
        this.planRepository = planRepository;
        this.executionRepository = executionRepository;
        this.upgradeTaskRepository = upgradeTaskRepository;
        this.messageStateRepository = messageStateRepository;
        this.eligibilityRule = eligibilityRule;
        this.compatibilityRule = compatibilityRule;
        this.stateService = stateService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public RetryAuthorizationOutcome authorize(UUID diagnosticTaskId, int planVersion,
                                                boolean acknowledged, String actor) {
        RetryPlan plan = planRepository.findLocked(diagnosticTaskId, planVersion)
                .orElseThrow(() -> new RetryApprovalRejectedException("RETRY_PLAN_NOT_FOUND"));
        var existing = executionRepository.findByRetryPlanId(plan.getId());
        if (existing.isPresent()) {
            return RetryAuthorizationOutcome.accepted(existing.get(), false);
        }
        if (!acknowledged) {
            recordRejection(plan, actor, "EXPLICIT_APPROVAL_REQUIRED", false);
            return RetryAuthorizationOutcome.rejected("EXPLICIT_APPROVAL_REQUIRED");
        }

        Instant now = Instant.now(clock);
        if (!now.isBefore(plan.getExpiresAt())) {
            recordRejection(plan, actor, "RETRY_PLAN_EXPIRED", true);
            return RetryAuthorizationOutcome.rejected("RETRY_PLAN_EXPIRED");
        }
        if (plan.getDiagnosticTask().getState() != DiagnosticState.WAITING_APPROVAL
                || !"PENDING_APPROVAL".equals(plan.getStatus())) {
            recordRejection(plan, actor, "RETRY_PLAN_NOT_PENDING", true);
            return RetryAuthorizationOutcome.rejected("RETRY_PLAN_NOT_PENDING");
        }

        UpgradeTask task = upgradeTaskRepository.findById(plan.getSnapshot().upgradeTaskId())
                .orElse(null);
        if (task == null || snapshotChanged(plan, task)) {
            recordRejection(plan, actor, "RETRY_PLAN_SNAPSHOT_CHANGED", true);
            return RetryAuthorizationOutcome.rejected("RETRY_PLAN_SNAPSHOT_CHANGED");
        }

        List<MessageState> messages = messageStateRepository.findByUpgradeTaskOrderByObservedAtAsc(task);
        if (messages.isEmpty()) {
            recordRejection(plan, actor, "RETRY_PLAN_SNAPSHOT_CHANGED", true);
            return RetryAuthorizationOutcome.rejected("RETRY_PLAN_SNAPSHOT_CHANGED");
        }
        MessageState message = messages.get(messages.size() - 1);
        VersionCompatibilityDecision compatibility = compatibilityRule.evaluate(new VersionCompatibilityFacts(
                task.getDevice().getModel(), task.getDevice().getCurrentVersion(),
                task.getTargetFirmwareVersion().getVersion(), task.getTargetFirmwareVersion().getReleaseStatus(),
                task.getTargetFirmwareVersion().getCompatibleModels()));
        RetryEligibilityDecision currentDecision = eligibilityRule.evaluate(new RetryEligibilityFacts(
                task.getId(), task.getVersion(), task.getStatus().name(), task.getFailureCode(),
                task.getDevice().isOnline(), compatibility.compatible(), task.getRetryCount(), task.getMaxRetries(),
                1, message.getSendStatus(), message.getCallbackStatus()));
        if (!currentDecision.eligible() || currentPreconditionsChanged(plan, task, message, compatibility)) {
            recordRejection(plan, actor, "RETRY_PLAN_SNAPSHOT_CHANGED", true);
            return RetryAuthorizationOutcome.rejected("RETRY_PLAN_SNAPSHOT_CHANGED");
        }

        plan.approve(actor, now);
        String idempotencyKey = "retry-plan:" + plan.getId() + ":v" + plan.getPlanVersion();
        RetryExecution execution = executionRepository.save(new RetryExecution(
                UUID.randomUUID(), plan, idempotencyKey, now));
        stateService.transition(diagnosticTaskId, DiagnosticState.EXECUTING, actor);
        auditService.append(actor, "RETRY_PLAN", plan.getId().toString(), "RETRY_APPROVED", "APPROVED",
                diagnosticTaskId.toString(), "Operator approved the exact retry plan snapshot",
                Map.of("planVersion", planVersion, "executionId", execution.getId().toString()));
        auditService.append("system", "RETRY_EXECUTION", execution.getId().toString(),
                "EXECUTION_INTENT_RECORDED", "RECORDED", diagnosticTaskId.toString(),
                "Execution intent committed before calling the simulator",
                Map.of("idempotencyKey", idempotencyKey));
        return RetryAuthorizationOutcome.accepted(execution, true);
    }

    private boolean snapshotChanged(RetryPlan plan, UpgradeTask task) {
        return task.getVersion() != plan.getSnapshot().taskVersion()
                || !task.getStatus().name().equals(plan.getSnapshot().taskStatus())
                || !task.getFailureCode().equals(plan.getSnapshot().failureCode())
                || !task.getTargetFirmwareVersion().getVersion().equals(plan.getSnapshot().targetVersion());
    }

    private boolean currentPreconditionsChanged(RetryPlan plan, UpgradeTask task, MessageState message,
                                                VersionCompatibilityDecision compatibility) {
        return task.getRetryCount() != plan.getSnapshot().retryCount()
                || task.getMaxRetries() != plan.getSnapshot().maxRetries()
                || task.getDevice().isOnline() != plan.getSnapshot().deviceOnline()
                || compatibility.compatible() != plan.getSnapshot().versionCompatible()
                || !message.getSendStatus().equals(plan.getSnapshot().messageSendStatus())
                || !message.getCallbackStatus().equals(plan.getSnapshot().callbackStatus());
    }

    private void recordRejection(RetryPlan plan, String actor, String code, boolean invalidate) {
        if (invalidate) {
            plan.reject("RETRY_PLAN_EXPIRED".equals(code) ? "EXPIRED" : "INVALIDATED");
            if (plan.getDiagnosticTask().getState() == DiagnosticState.WAITING_APPROVAL) {
                stateService.transition(plan.getDiagnosticTask().getId(), DiagnosticState.INCOMPLETE, actor);
            }
        }
        auditService.append(actor, "RETRY_PLAN", plan.getId().toString(), "RETRY_REJECTED", code,
                plan.getDiagnosticTask().getId().toString(), "Retry approval was rejected by backend rules",
                Map.of("reasonCode", code, "planVersion", plan.getPlanVersion()));
    }
}
