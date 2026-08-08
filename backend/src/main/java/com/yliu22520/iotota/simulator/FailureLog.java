package com.yliu22520.iotota.simulator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "failure_log")
public class FailureLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upgrade_task_id", nullable = false)
    private UpgradeTask upgradeTask;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(nullable = false)
    private String level;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private boolean simulated;

    protected FailureLog() {
    }

    public FailureLog(UpgradeTask upgradeTask, Instant observedAt, String level, String code,
                      String message, String source, boolean simulated) {
        this.upgradeTask = upgradeTask;
        this.observedAt = observedAt;
        this.level = level;
        this.code = code;
        this.message = message;
        this.source = source;
        this.simulated = simulated;
    }

    public Long getId() { return id; }
    public Instant getObservedAt() { return observedAt; }
    public String getLevel() { return level; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getSource() { return source; }
    public boolean isSimulated() { return simulated; }
}
