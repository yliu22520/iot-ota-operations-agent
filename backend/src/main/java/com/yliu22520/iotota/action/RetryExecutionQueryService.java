package com.yliu22520.iotota.action;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class RetryExecutionQueryService {

    private final RetryExecutionRepository executionRepository;

    public RetryExecutionQueryService(RetryExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    @Transactional(readOnly = true)
    public RetryExecutionView view(UUID executionId, boolean idempotentReplay) {
        RetryExecution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException("Retry execution not found: " + executionId));
        Map<String, Object> result = execution.getResult();
        String verificationStatus = result == null
                ? "PENDING"
                : String.valueOf(result.getOrDefault("verificationStatus", "PENDING"));
        RetryPlan plan = execution.getRetryPlan();
        return new RetryExecutionView(plan.getDiagnosticTask().getId(), plan.getId(), plan.getPlanVersion(),
                execution.getId(), plan.getDiagnosticTask().getState().name(), execution.getStatus(),
                verificationStatus, execution.getIdempotencyKey(), idempotentReplay, execution.getUpdatedAt());
    }
}
