package com.yliu22520.iotota.simulator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SimulatorService {

    private final UpgradeTaskRepository upgradeTaskRepository;
    private final FailureLogRepository failureLogRepository;
    private final MessageStateRepository messageStateRepository;

    public SimulatorService(UpgradeTaskRepository upgradeTaskRepository,
                            FailureLogRepository failureLogRepository,
                            MessageStateRepository messageStateRepository) {
        this.upgradeTaskRepository = upgradeTaskRepository;
        this.failureLogRepository = failureLogRepository;
        this.messageStateRepository = messageStateRepository;
    }

    @Transactional(readOnly = true)
    public SimulatorDtos.TaskList listFinalFailureTasks() {
        List<SimulatorDtos.TaskSummary> items = upgradeTaskRepository
                .findByStatusOrderByFailedAtDesc(UpgradeTaskStatus.FINAL_FAILURE)
                .stream()
                .map(this::toSummary)
                .toList();
        return new SimulatorDtos.TaskList(items, true);
    }

    @Transactional(readOnly = true)
    public SimulatorDtos.TaskDetail getTask(UUID id) {
        UpgradeTask task = upgradeTaskRepository.findById(id)
                .orElseThrow(() -> new UpgradeTaskNotFoundException(id));

        List<SimulatorDtos.FailureLogView> logs = failureLogRepository
                .findByUpgradeTaskOrderByObservedAtAsc(task)
                .stream()
                .map(log -> new SimulatorDtos.FailureLogView(log.getId(), log.getObservedAt(), log.getLevel(),
                        log.getCode(), log.getMessage(), log.getSource(), log.isSimulated()))
                .toList();
        List<SimulatorDtos.MessageStateView> messages = messageStateRepository
                .findByUpgradeTaskOrderByObservedAtAsc(task)
                .stream()
                .map(message -> new SimulatorDtos.MessageStateView(message.getId(), message.getMessageType(),
                        message.getSendStatus(), message.getCallbackStatus(), message.getObservedAt(),
                        message.getDetail(), message.isSimulated()))
                .toList();

        return new SimulatorDtos.TaskDetail(
                toSummary(task),
                new SimulatorDtos.DeviceView(task.getDevice().getId(), task.getDevice().getSerialNumber(),
                        task.getDevice().getModel(), task.getDevice().getCurrentVersion(), task.getDevice().isOnline(),
                        task.getDevice().getStorageAvailableMb(), task.getDevice().isSimulated()),
                new SimulatorDtos.FirmwareVersionView(task.getTargetFirmwareVersion().getId(),
                        task.getTargetFirmwareVersion().getVersion(), task.getTargetFirmwareVersion().getReleaseStatus(),
                        task.getTargetFirmwareVersion().getCompatibleModels(), task.getTargetFirmwareVersion().getChecksum(),
                        task.getTargetFirmwareVersion().getReleasedAt(), task.getTargetFirmwareVersion().isSimulated()),
                logs,
                messages,
                true);
    }

    private SimulatorDtos.TaskSummary toSummary(UpgradeTask task) {
        return new SimulatorDtos.TaskSummary(
                task.getId(),
                task.getDevice().getId(),
                task.getDevice().getSerialNumber(),
                task.getTargetFirmwareVersion().getVersion(),
                task.getFailedAt(),
                task.getStatus().name(),
                task.getFailureCode(),
                task.getFailureSummary(),
                null,
                task.isSimulated());
    }
}
