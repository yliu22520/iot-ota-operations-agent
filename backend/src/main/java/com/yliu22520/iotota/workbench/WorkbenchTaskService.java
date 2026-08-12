package com.yliu22520.iotota.workbench;

import com.yliu22520.iotota.diagnosis.DiagnosticTaskRepository;
import com.yliu22520.iotota.simulator.SimulatorDtos;
import com.yliu22520.iotota.simulator.SimulatorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WorkbenchTaskService {

    private final SimulatorService simulatorService;
    private final DiagnosticTaskRepository diagnosticTaskRepository;

    public WorkbenchTaskService(SimulatorService simulatorService,
                                DiagnosticTaskRepository diagnosticTaskRepository) {
        this.simulatorService = simulatorService;
        this.diagnosticTaskRepository = diagnosticTaskRepository;
    }

    @Transactional(readOnly = true)
    public WorkbenchDtos.TaskList listFinalFailureTasks() {
        SimulatorDtos.TaskList tasks = simulatorService.listFinalFailureTasks();
        return new WorkbenchDtos.TaskList(
                tasks.items().stream().map(this::toSummary).toList(),
                tasks.simulated());
    }

    private WorkbenchDtos.TaskSummary toSummary(SimulatorDtos.TaskSummary task) {
        var latestDiagnosis = diagnosticTaskRepository.findAllByUpgradeTaskIdOrderByUpdatedAtDesc(task.id())
                .stream().findFirst();
        return new WorkbenchDtos.TaskSummary(
                task.id(),
                task.deviceId(),
                task.deviceSerialNumber(),
                task.targetVersion(),
                task.failedAt(),
                task.status(),
                task.failureCode(),
                task.failureSummary(),
                latestDiagnosis.map(diagnosis -> diagnosis.getState().name()).orElse("NOT_STARTED"),
                latestDiagnosis.map(diagnosis -> diagnosis.getId()).orElse(null),
                task.simulated());
    }
}
