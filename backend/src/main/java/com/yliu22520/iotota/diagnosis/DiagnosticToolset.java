package com.yliu22520.iotota.diagnosis;

import java.util.List;
import java.util.UUID;

/** Only the narrow read tools needed by the version-incompatibility tracer bullet. */
public interface DiagnosticToolset {

    StructuredToolResult<UpgradeTaskToolData> getUpgradeTask(UUID upgradeTaskId);

    StructuredToolResult<DeviceStateToolData> getDeviceState(String deviceId);

    StructuredToolResult<FirmwareVersionToolData> getFirmwareVersion(String firmwareVersionId);

    StructuredToolResult<VersionCompatibilityDecision> getVersionCompatibility(UUID upgradeTaskId);

    StructuredToolResult<List<FailureLogToolData>> getFailureLogs(UUID upgradeTaskId);
}
