package com.yliu22520.iotota.simulator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SimulatorDtos {

    private SimulatorDtos() {
    }

    public record TaskSummary(
            UUID id,
            String deviceId,
            String deviceSerialNumber,
            String targetVersion,
            Instant failedAt,
            String status,
            String failureCode,
            String failureSummary,
            String diagnosticStatus,
            boolean simulated) {
    }

    public record DeviceView(
            String id,
            String serialNumber,
            String model,
            String currentVersion,
            boolean online,
            int storageAvailableMb,
            boolean simulated) {
    }

    public record FirmwareVersionView(
            String id,
            String version,
            String releaseStatus,
            String compatibleModels,
            String checksum,
            Instant releasedAt,
            boolean simulated) {
    }

    public record FailureLogView(
            Long id,
            Instant observedAt,
            String level,
            String code,
            String message,
            String source,
            boolean simulated) {
    }

    public record MessageStateView(
            Long id,
            String messageType,
            String sendStatus,
            String callbackStatus,
            Instant observedAt,
            String detail,
            boolean simulated) {
    }

    public record TaskDetail(
            TaskSummary task,
            DeviceView device,
            FirmwareVersionView firmwareVersion,
            List<FailureLogView> failureLogs,
            List<MessageStateView> messageStates,
            boolean simulated) {
    }

    public record TaskList(List<TaskSummary> items, boolean simulated) {
    }
}
