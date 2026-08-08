package com.yliu22520.iotota.simulator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upgrade_task")
public class UpgradeTask {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @jakarta.persistence.JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @jakarta.persistence.JoinColumn(name = "target_firmware_version_id", nullable = false)
    private FirmwareVersion targetFirmwareVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UpgradeTaskStatus status;

    @Column(name = "failure_code", nullable = false)
    private String failureCode;

    @Column(name = "failure_summary", nullable = false)
    private String failureSummary;

    @Column(name = "failed_at", nullable = false)
    private Instant failedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private boolean simulated;

    protected UpgradeTask() {
    }

    public UpgradeTask(UUID id, Device device, FirmwareVersion targetFirmwareVersion,
                        UpgradeTaskStatus status, String failureCode, String failureSummary,
                        Instant failedAt, boolean simulated) {
        this.id = id;
        this.device = device;
        this.targetFirmwareVersion = targetFirmwareVersion;
        this.status = status;
        this.failureCode = failureCode;
        this.failureSummary = failureSummary;
        this.failedAt = failedAt;
        this.simulated = simulated;
    }

    public UUID getId() { return id; }
    public Device getDevice() { return device; }
    public FirmwareVersion getTargetFirmwareVersion() { return targetFirmwareVersion; }
    public UpgradeTaskStatus getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public String getFailureSummary() { return failureSummary; }
    public Instant getFailedAt() { return failedAt; }
    public boolean isSimulated() { return simulated; }
}
