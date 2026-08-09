package com.yliu22520.iotota.diagnosis;

import com.yliu22520.iotota.audit.AuditService;
import com.yliu22520.iotota.simulator.UpgradeTask;
import com.yliu22520.iotota.simulator.UpgradeTaskNotFoundException;
import com.yliu22520.iotota.simulator.UpgradeTaskRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DiagnosticTaskService {

    private final DiagnosticTaskRepository diagnosticTaskRepository;
    private final DiagnosticReportRepository diagnosticReportRepository;
    private final UpgradeTaskRepository upgradeTaskRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public DiagnosticTaskService(DiagnosticTaskRepository diagnosticTaskRepository,
                                 DiagnosticReportRepository diagnosticReportRepository,
                                 UpgradeTaskRepository upgradeTaskRepository,
                                 AuditService auditService,
                                 ApplicationEventPublisher eventPublisher,
                                 Clock clock) {
        this.diagnosticTaskRepository = diagnosticTaskRepository;
        this.diagnosticReportRepository = diagnosticReportRepository;
        this.upgradeTaskRepository = upgradeTaskRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public DiagnosticApiModels.StartResponse start(UUID upgradeTaskId, String actor) {
        UpgradeTask upgradeTask = upgradeTaskRepository.findById(upgradeTaskId)
                .orElseThrow(() -> new UpgradeTaskNotFoundException(upgradeTaskId));
        Optional<DiagnosticTask> active = diagnosticTaskRepository.findActiveByUpgradeTaskId(upgradeTaskId);
        if (active.isPresent()) {
            throw new DiagnosticAlreadyActiveException(active.get().getId());
        }

        Instant now = Instant.now(clock);
        UUID diagnosticTaskId = UUID.randomUUID();
        DiagnosticTask diagnosticTask = new DiagnosticTask(diagnosticTaskId, upgradeTask, now);
        diagnosticTaskRepository.saveAndFlush(diagnosticTask);

        auditService.append(actor, "DIAGNOSTIC_TASK", diagnosticTaskId.toString(),
                "DIAGNOSTIC_CREATED", "ACCEPTED", diagnosticTaskId.toString(),
                "Read-only diagnosis created", Map.of("upgradeTaskId", upgradeTaskId.toString(),
                        "state", DiagnosticState.CREATED.name()));
        eventPublisher.publishEvent(new DiagnosticCreatedEvent(diagnosticTaskId, actor));
        return new DiagnosticApiModels.StartResponse(diagnosticTaskId, upgradeTaskId,
                DiagnosticState.CREATED.name(), "/api/v1/diagnostic-tasks/" + diagnosticTaskId);
    }

    @Transactional(readOnly = true)
    public DiagnosticApiModels.TaskView get(UUID diagnosticTaskId) {
        DiagnosticTask task = diagnosticTaskRepository.findById(diagnosticTaskId)
                .orElseThrow(() -> new DiagnosticTaskNotFoundException(diagnosticTaskId));
        return toView(task);
    }

    @Transactional(readOnly = true)
    public Optional<DiagnosticApiModels.TaskView> findActiveForUpgradeTask(UUID upgradeTaskId) {
        return diagnosticTaskRepository.findActiveByUpgradeTaskId(upgradeTaskId).map(this::toView);
    }

    private DiagnosticApiModels.TaskView toView(DiagnosticTask task) {
        DiagnosticReportDocument report = diagnosticReportRepository.findByDiagnosticTaskId(task.getId())
                .map(DiagnosticReport::getReport)
                .orElse(null);
        String state = task.getState().name();
        return new DiagnosticApiModels.TaskView(task.getId(), task.getUpgradeTask().getId(), state, state,
                task.isTerminal(), task.getCreatedAt(), task.getUpdatedAt(), report);
    }
}
