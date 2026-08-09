package com.yliu22520.iotota.diagnosis;

import com.yliu22520.iotota.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class DiagnosticStateService {

    private final DiagnosticTaskRepository diagnosticTaskRepository;
    private final DiagnosticReportRepository diagnosticReportRepository;
    private final DiagnosticStateMachine stateMachine;
    private final AuditService auditService;
    private final Clock clock;

    public DiagnosticStateService(DiagnosticTaskRepository diagnosticTaskRepository,
                                  DiagnosticReportRepository diagnosticReportRepository,
                                  DiagnosticStateMachine stateMachine,
                                  AuditService auditService,
                                  Clock clock) {
        this.diagnosticTaskRepository = diagnosticTaskRepository;
        this.diagnosticReportRepository = diagnosticReportRepository;
        this.stateMachine = stateMachine;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public DiagnosticState transition(UUID diagnosticTaskId, DiagnosticState next, String actor) {
        DiagnosticTask task = find(diagnosticTaskId);
        DiagnosticState previous = task.getState();
        task.transitionTo(next, stateMachine, Instant.now(clock));
        diagnosticTaskRepository.saveAndFlush(task);
        auditService.append(actor, "DIAGNOSTIC_TASK", diagnosticTaskId.toString(), "STATE_CHANGED", "RECORDED",
                diagnosticTaskId.toString(), "Diagnostic state changed", Map.of("from", previous.name(),
                        "to", next.name()));
        return next;
    }

    @Transactional
    public void persistReportAndMarkReady(UUID diagnosticTaskId, DiagnosticReportDocument report, String actor) {
        DiagnosticTask task = find(diagnosticTaskId);
        if (task.getState() != DiagnosticState.INVESTIGATING) {
            throw new IllegalStateException("Report can only be generated while investigating");
        }
        Instant now = Instant.now(clock);
        diagnosticReportRepository.save(new DiagnosticReport(UUID.randomUUID(), task, report, now));
        task.transitionTo(DiagnosticState.REPORT_READY, stateMachine, now);
        diagnosticTaskRepository.saveAndFlush(task);
        auditService.append(actor, "DIAGNOSTIC_TASK", diagnosticTaskId.toString(), "REPORT_GENERATED", "RECORDED",
                diagnosticTaskId.toString(), "Structured diagnostic report generated",
                Map.of("schemaVersion", report.schemaVersion(), "rootCauseCode", report.rootCauseCode()));
    }

    @Transactional
    public void persistIncompleteReport(UUID diagnosticTaskId, DiagnosticReportDocument report, String actor) {
        DiagnosticTask task = find(diagnosticTaskId);
        if (task.isTerminal()) {
            return;
        }
        Instant now = Instant.now(clock);
        diagnosticReportRepository.save(new DiagnosticReport(UUID.randomUUID(), task, report, now));
        if (task.getState() == DiagnosticState.CREATED) {
            task.transitionTo(DiagnosticState.INVESTIGATING, stateMachine, now);
        }
        task.transitionTo(DiagnosticState.INCOMPLETE, stateMachine, now);
        diagnosticTaskRepository.saveAndFlush(task);
        auditService.append(actor, "DIAGNOSTIC_TASK", diagnosticTaskId.toString(), "REPORT_GENERATED", "INCOMPLETE",
                diagnosticTaskId.toString(), "Structured report records an evidence gap", Map.of(
                        "schemaVersion", report.schemaVersion(), "rootCauseCode", report.rootCauseCode()));
    }

    @Transactional(readOnly = true)
    public DiagnosticState currentState(UUID diagnosticTaskId) {
        return find(diagnosticTaskId).getState();
    }

    private DiagnosticTask find(UUID diagnosticTaskId) {
        return diagnosticTaskRepository.findById(diagnosticTaskId)
                .orElseThrow(() -> new DiagnosticTaskNotFoundException(diagnosticTaskId));
    }
}
