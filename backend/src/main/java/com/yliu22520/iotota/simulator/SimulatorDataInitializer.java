package com.yliu22520.iotota.simulator;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class SimulatorDataInitializer implements ApplicationRunner {

    private static final Instant SEED_TIME = Instant.parse("2026-08-08T00:00:00Z");

    private final DeviceRepository deviceRepository;
    private final FirmwareVersionRepository firmwareVersionRepository;
    private final UpgradeTaskRepository upgradeTaskRepository;
    private final FailureLogRepository failureLogRepository;
    private final MessageStateRepository messageStateRepository;

    public SimulatorDataInitializer(DeviceRepository deviceRepository,
                                    FirmwareVersionRepository firmwareVersionRepository,
                                    UpgradeTaskRepository upgradeTaskRepository,
                                    FailureLogRepository failureLogRepository,
                                    MessageStateRepository messageStateRepository) {
        this.deviceRepository = deviceRepository;
        this.firmwareVersionRepository = firmwareVersionRepository;
        this.upgradeTaskRepository = upgradeTaskRepository;
        this.failureLogRepository = failureLogRepository;
        this.messageStateRepository = messageStateRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Device deviceIncompatible = deviceRepository.findById("device-sim-001")
                .orElseGet(() -> deviceRepository.save(new Device(
                        "device-sim-001", "SIM-EDGE-001", "EDGE-CAMERA-A", "1.0.0", true, 2048, true, SEED_TIME)));
        Device deviceCallback = deviceRepository.findById("device-sim-002")
                .orElseGet(() -> deviceRepository.save(new Device(
                        "device-sim-002", "SIM-SENSOR-002", "SENSOR-HUB-B", "1.0.0", true, 4096, true, SEED_TIME)));

        FirmwareVersion incompatibleFirmware = firmwareVersionRepository.findById("firmware-sim-200")
                .orElseGet(() -> firmwareVersionRepository.save(new FirmwareVersion(
                        "firmware-sim-200", "2.0.0", "RELEASED", "SENSOR-HUB-B", "sha256:sim-200", SEED_TIME, true)));
        FirmwareVersion callbackFirmware = firmwareVersionRepository.findById("firmware-sim-110")
                .orElseGet(() -> firmwareVersionRepository.save(new FirmwareVersion(
                        "firmware-sim-110", "1.1.0", "RELEASED", "SENSOR-HUB-B", "sha256:sim-110", SEED_TIME, true)));

        UpgradeTask incompatibleTask = upgradeTaskRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000101"))
                .orElseGet(() -> upgradeTaskRepository.save(new UpgradeTask(
                        UUID.fromString("00000000-0000-0000-0000-000000000101"), deviceIncompatible, incompatibleFirmware,
                        UpgradeTaskStatus.FINAL_FAILURE, "VERSION_INCOMPATIBLE",
                        "目标固件不支持设备型号 EDGE-CAMERA-A", SEED_TIME.plusSeconds(60), true)));
        UpgradeTask callbackTask = upgradeTaskRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000102"))
                .orElseGet(() -> upgradeTaskRepository.save(new UpgradeTask(
                        UUID.fromString("00000000-0000-0000-0000-000000000102"), deviceCallback, callbackFirmware,
                        UpgradeTaskStatus.FINAL_FAILURE, "CALLBACK_TIMEOUT",
                        "设备在线且命令已发送，但在超时时间内未收到回调", SEED_TIME.plusSeconds(120), true)));

        if (failureLogRepository.findByUpgradeTaskOrderByObservedAtAsc(incompatibleTask).isEmpty()) {
            failureLogRepository.save(new FailureLog(incompatibleTask, SEED_TIME.plusSeconds(61), "ERROR",
                    "VERSION_INCOMPATIBLE", "firmware 2.0.0 is not compatible with model EDGE-CAMERA-A",
                    "simulator.compatibility-rule", true));
        }
        if (failureLogRepository.findByUpgradeTaskOrderByObservedAtAsc(callbackTask).isEmpty()) {
            failureLogRepository.save(new FailureLog(callbackTask, SEED_TIME.plusSeconds(121), "WARN",
                    "CALLBACK_TIMEOUT", "upgrade command accepted but callback deadline elapsed",
                    "simulator.callback-monitor", true));
        }
        if (messageStateRepository.findByUpgradeTaskOrderByObservedAtAsc(incompatibleTask).isEmpty()) {
            messageStateRepository.save(new MessageState(incompatibleTask, "UPGRADE_COMMAND", "NOT_SENT",
                    "NOT_APPLICABLE", SEED_TIME.plusSeconds(60), "compatibility rule rejected before dispatch", true));
        }
        if (messageStateRepository.findByUpgradeTaskOrderByObservedAtAsc(callbackTask).isEmpty()) {
            messageStateRepository.save(new MessageState(callbackTask, "UPGRADE_COMMAND", "SENT",
                    "TIMEOUT", SEED_TIME.plusSeconds(121), "command consumed; device callback missing", true));
        }
    }
}
