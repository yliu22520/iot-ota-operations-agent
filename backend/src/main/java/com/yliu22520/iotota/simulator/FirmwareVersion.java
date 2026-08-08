package com.yliu22520.iotota.simulator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "firmware_version")
public class FirmwareVersion {

    @Id
    private String id;

    @Column(nullable = false)
    private String version;

    @Column(name = "release_status", nullable = false)
    private String releaseStatus;

    @Column(name = "compatible_models", nullable = false)
    private String compatibleModels;

    @Column(nullable = false)
    private String checksum;

    @Column(name = "released_at", nullable = false)
    private Instant releasedAt;

    @Column(nullable = false)
    private boolean simulated;

    protected FirmwareVersion() {
    }

    public FirmwareVersion(String id, String version, String releaseStatus, String compatibleModels,
                           String checksum, Instant releasedAt, boolean simulated) {
        this.id = id;
        this.version = version;
        this.releaseStatus = releaseStatus;
        this.compatibleModels = compatibleModels;
        this.checksum = checksum;
        this.releasedAt = releasedAt;
        this.simulated = simulated;
    }

    public String getId() { return id; }
    public String getVersion() { return version; }
    public String getReleaseStatus() { return releaseStatus; }
    public String getCompatibleModels() { return compatibleModels; }
    public String getChecksum() { return checksum; }
    public Instant getReleasedAt() { return releasedAt; }
    public boolean isSimulated() { return simulated; }
}
