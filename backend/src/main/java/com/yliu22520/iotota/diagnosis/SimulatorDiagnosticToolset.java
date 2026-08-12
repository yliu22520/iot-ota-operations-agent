package com.yliu22520.iotota.diagnosis;

import com.yliu22520.iotota.knowledge.KnowledgeRetriever;
import com.yliu22520.iotota.knowledge.KnowledgeSearchQuery;
import com.yliu22520.iotota.knowledge.KnowledgeSearchResult;
import com.yliu22520.iotota.simulator.Device;
import com.yliu22520.iotota.simulator.DeviceRepository;
import com.yliu22520.iotota.simulator.FailureLog;
import com.yliu22520.iotota.simulator.FailureLogRepository;
import com.yliu22520.iotota.simulator.FirmwareVersion;
import com.yliu22520.iotota.simulator.FirmwareVersionRepository;
import com.yliu22520.iotota.simulator.MessageState;
import com.yliu22520.iotota.simulator.MessageStateRepository;
import com.yliu22520.iotota.simulator.UpgradeTask;
import com.yliu22520.iotota.simulator.UpgradeTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SimulatorDiagnosticToolset implements DiagnosticToolset {

    private static final String SOURCE_TASK = "simulator.upgrade-task";
    private static final String SOURCE_DEVICE = "simulator.device-state";
    private static final String SOURCE_FIRMWARE = "simulator.firmware-catalogue";
    private static final String SOURCE_COMPATIBILITY = "backend.version-compatibility-rule";
    private static final String SOURCE_LOG = "simulator.failure-log";
    private static final String SOURCE_MESSAGE = "simulator.message-state";
    private static final String SOURCE_KNOWLEDGE = "local.knowledge.pgvector";

    private final UpgradeTaskRepository upgradeTaskRepository;
    private final DeviceRepository deviceRepository;
    private final FirmwareVersionRepository firmwareVersionRepository;
    private final FailureLogRepository failureLogRepository;
    private final MessageStateRepository messageStateRepository;
    private final VersionCompatibilityRule compatibilityRule;
    private final KnowledgeRetriever knowledgeRetriever;
    private final Clock clock;

    public SimulatorDiagnosticToolset(UpgradeTaskRepository upgradeTaskRepository,
                                      DeviceRepository deviceRepository,
                                      FirmwareVersionRepository firmwareVersionRepository,
                                      FailureLogRepository failureLogRepository,
                                      MessageStateRepository messageStateRepository,
                                      VersionCompatibilityRule compatibilityRule,
                                      KnowledgeRetriever knowledgeRetriever,
                                      Clock clock) {
        this.upgradeTaskRepository = upgradeTaskRepository;
        this.deviceRepository = deviceRepository;
        this.firmwareVersionRepository = firmwareVersionRepository;
        this.failureLogRepository = failureLogRepository;
        this.messageStateRepository = messageStateRepository;
        this.compatibilityRule = compatibilityRule;
        this.knowledgeRetriever = knowledgeRetriever;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public StructuredToolResult<UpgradeTaskToolData> getUpgradeTask(UUID upgradeTaskId) {
        Instant observedAt = Instant.now(clock);
        return upgradeTaskRepository.findById(upgradeTaskId)
                .map(task -> StructuredToolResult.success("getUpgradeTask", "upgrade-task:" + task.getId(),
                        SOURCE_TASK, observedAt, new UpgradeTaskToolData(task.getId(), task.getStatus().name(),
                                task.getFailureCode(), task.getFailureSummary(), task.getFailedAt(),
                                task.getDevice().getId(), task.getTargetFirmwareVersion().getId(),
                                task.getVersion(), task.getRetryCount(), task.getMaxRetries())))
                .orElseGet(() -> StructuredToolResult.failure("getUpgradeTask", "upgrade-task:" + upgradeTaskId,
                        SOURCE_TASK, observedAt, "Upgrade task not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public StructuredToolResult<DeviceStateToolData> getDeviceState(String deviceId) {
        Instant observedAt = Instant.now(clock);
        return deviceRepository.findById(deviceId)
                .map(device -> StructuredToolResult.success("getDeviceState", "device:" + device.getId(),
                        SOURCE_DEVICE, observedAt, toDeviceData(device)))
                .orElseGet(() -> StructuredToolResult.failure("getDeviceState", "device:" + deviceId,
                        SOURCE_DEVICE, observedAt, "Device not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public StructuredToolResult<FirmwareVersionToolData> getFirmwareVersion(String firmwareVersionId) {
        Instant observedAt = Instant.now(clock);
        return firmwareVersionRepository.findById(firmwareVersionId)
                .map(version -> StructuredToolResult.success("getFirmwareVersion", "firmware:" + version.getId(),
                        SOURCE_FIRMWARE, observedAt, toFirmwareData(version)))
                .orElseGet(() -> StructuredToolResult.failure("getFirmwareVersion", "firmware:" + firmwareVersionId,
                        SOURCE_FIRMWARE, observedAt, "Firmware version not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public StructuredToolResult<VersionCompatibilityDecision> getVersionCompatibility(UUID upgradeTaskId) {
        Instant observedAt = Instant.now(clock);
        return upgradeTaskRepository.findById(upgradeTaskId)
                .map(task -> {
                    VersionCompatibilityFacts facts = new VersionCompatibilityFacts(
                            task.getDevice().getModel(), task.getDevice().getCurrentVersion(),
                            task.getTargetFirmwareVersion().getVersion(),
                            task.getTargetFirmwareVersion().getReleaseStatus(),
                            task.getTargetFirmwareVersion().getCompatibleModels());
                    return StructuredToolResult.success("getVersionCompatibility",
                            "version-compatibility:" + task.getId(), SOURCE_COMPATIBILITY, observedAt,
                            compatibilityRule.evaluate(facts));
                })
                .orElseGet(() -> StructuredToolResult.failure("getVersionCompatibility",
                        "version-compatibility:" + upgradeTaskId, SOURCE_COMPATIBILITY, observedAt,
                        "Upgrade task not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public StructuredToolResult<List<FailureLogToolData>> getFailureLogs(UUID upgradeTaskId) {
        Instant observedAt = Instant.now(clock);
        return upgradeTaskRepository.findById(upgradeTaskId)
                .map(task -> StructuredToolResult.success("getFailureLogs", "failure-logs:" + task.getId(),
                        SOURCE_LOG, observedAt, failureLogRepository.findByUpgradeTaskOrderByObservedAtAsc(task)
                                .stream().map(this::toFailureLogData).toList()))
                .orElseGet(() -> StructuredToolResult.failure("getFailureLogs", "failure-logs:" + upgradeTaskId,
                        SOURCE_LOG, observedAt, "Upgrade task not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public StructuredToolResult<List<MessageStateToolData>> getMessageStates(UUID upgradeTaskId) {
        Instant observedAt = Instant.now(clock);
        return upgradeTaskRepository.findById(upgradeTaskId)
                .map(task -> StructuredToolResult.success("getMessageStates", "message-state:" + task.getId(),
                        SOURCE_MESSAGE, observedAt, messageStateRepository.findByUpgradeTaskOrderByObservedAtAsc(task)
                                .stream().map(this::toMessageStateData).toList()))
                .orElseGet(() -> StructuredToolResult.failure("getMessageStates", "message-state:" + upgradeTaskId,
                        SOURCE_MESSAGE, observedAt, "Upgrade task not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public StructuredToolResult<KnowledgeSearchResult> searchKnowledge(UUID upgradeTaskId) {
        Instant observedAt = Instant.now(clock);
        return upgradeTaskRepository.findById(upgradeTaskId)
                .map(task -> {
                    KnowledgeSearchResult search = knowledgeRetriever.search(new KnowledgeSearchQuery(
                            task.getFailureCode(), task.getFailureSummary(), "ota-upgrade",
                            task.getTargetFirmwareVersion().getVersion()));
                    if (search.success()) {
                        return StructuredToolResult.success("searchKnowledge", "knowledge-search:" + task.getId(),
                                SOURCE_KNOWLEDGE, observedAt, search);
                    }
                    return StructuredToolResult.<KnowledgeSearchResult>failure("searchKnowledge",
                            "knowledge-search:" + task.getId(), SOURCE_KNOWLEDGE, observedAt, search.error());
                })
                .orElseGet(() -> StructuredToolResult.failure("searchKnowledge",
                        "knowledge-search:" + upgradeTaskId, SOURCE_KNOWLEDGE, observedAt,
                        "Upgrade task not found"));
    }

    private DeviceStateToolData toDeviceData(Device device) {
        return new DeviceStateToolData(device.getId(), device.getSerialNumber(), device.getModel(),
                device.getCurrentVersion(), device.isOnline(), device.getStorageAvailableMb());
    }

    private FirmwareVersionToolData toFirmwareData(FirmwareVersion version) {
        return new FirmwareVersionToolData(version.getId(), version.getVersion(), version.getReleaseStatus(),
                version.getCompatibleModels(), version.getChecksum(), version.getReleasedAt());
    }

    private FailureLogToolData toFailureLogData(FailureLog log) {
        return new FailureLogToolData(log.getId(), log.getObservedAt(), log.getLevel(), log.getCode(),
                log.getMessage(), log.getSource());
    }

    private MessageStateToolData toMessageStateData(MessageState message) {
        return new MessageStateToolData(message.getId(), message.getMessageType(), message.getSendStatus(),
                message.getCallbackStatus(), message.getObservedAt(), message.getDetail());
    }
}
