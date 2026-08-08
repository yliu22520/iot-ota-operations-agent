package com.yliu22520.iotota.simulator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "device")
public class Device {

    @Id
    private String id;

    @Column(name = "serial_number", nullable = false, unique = true)
    private String serialNumber;

    @Column(nullable = false)
    private String model;

    @Column(name = "current_version", nullable = false)
    private String currentVersion;

    @Column(nullable = false)
    private boolean online;

    @Column(name = "storage_available_mb", nullable = false)
    private int storageAvailableMb;

    @Column(nullable = false)
    private boolean simulated;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Device() {
    }

    public Device(String id, String serialNumber, String model, String currentVersion,
                  boolean online, int storageAvailableMb, boolean simulated, Instant createdAt) {
        this.id = id;
        this.serialNumber = serialNumber;
        this.model = model;
        this.currentVersion = currentVersion;
        this.online = online;
        this.storageAvailableMb = storageAvailableMb;
        this.simulated = simulated;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getSerialNumber() { return serialNumber; }
    public String getModel() { return model; }
    public String getCurrentVersion() { return currentVersion; }
    public boolean isOnline() { return online; }
    public int getStorageAvailableMb() { return storageAvailableMb; }
    public boolean isSimulated() { return simulated; }
}
