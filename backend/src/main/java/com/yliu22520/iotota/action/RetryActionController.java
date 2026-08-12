package com.yliu22520.iotota.action;

import com.yliu22520.iotota.audit.AuditEvent;
import com.yliu22520.iotota.audit.AuditEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/diagnostic-tasks/{diagnosticTaskId}")
public class RetryActionController {

    private final RetryActionService actionService;
    private final AuditEventRepository auditEventRepository;

    public RetryActionController(RetryActionService actionService,
                                 AuditEventRepository auditEventRepository) {
        this.actionService = actionService;
        this.auditEventRepository = auditEventRepository;
    }

    @PostMapping("/retry-plan/approve")
    public RetryExecutionView approve(@PathVariable UUID diagnosticTaskId,
                                      @RequestBody ApprovalRequest request,
                                      Authentication authentication) {
        return actionService.approveAndExecute(diagnosticTaskId, request.planVersion(),
                request.acknowledged(), authentication.getName());
    }

    @GetMapping("/audit-events")
    public List<AuditEventView> auditEvents(@PathVariable UUID diagnosticTaskId) {
        return auditEventRepository.findByCorrelationIdOrderByOccurredAtAsc(diagnosticTaskId.toString())
                .stream().map(AuditEventView::from).toList();
    }

    @ExceptionHandler(RetryApprovalRejectedException.class)
    ResponseEntity<ProblemDetail> handleRejected(RetryApprovalRejectedException exception) {
        HttpStatus status = "RETRY_PLAN_NOT_FOUND".equals(exception.getCode())
                ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setProperty("code", exception.getCode());
        return ResponseEntity.status(status).body(problem);
    }

    public record ApprovalRequest(int planVersion, boolean acknowledged) {
    }

    public record AuditEventView(UUID id, Instant occurredAt, String actor, String objectType,
                                 String objectId, String action, String result, String summary,
                                 Map<String, Object> metadata) {
        static AuditEventView from(AuditEvent event) {
            return new AuditEventView(event.getId(), event.getOccurredAt(), event.getActor(), event.getObjectType(),
                    event.getObjectId(), event.getAction(), event.getResult(), event.getSummary(), event.getMetadata());
        }
    }
}
