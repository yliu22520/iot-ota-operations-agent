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
@Table(name = "message_state")
public class MessageState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upgrade_task_id", nullable = false)
    private UpgradeTask upgradeTask;

    @Column(name = "message_type", nullable = false)
    private String messageType;

    @Column(name = "send_status", nullable = false)
    private String sendStatus;

    @Column(name = "callback_status", nullable = false)
    private String callbackStatus;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(nullable = false)
    private String detail;

    @Column(nullable = false)
    private boolean simulated;

    protected MessageState() {
    }

    public MessageState(UpgradeTask upgradeTask, String messageType, String sendStatus,
                        String callbackStatus, Instant observedAt, String detail, boolean simulated) {
        this.upgradeTask = upgradeTask;
        this.messageType = messageType;
        this.sendStatus = sendStatus;
        this.callbackStatus = callbackStatus;
        this.observedAt = observedAt;
        this.detail = detail;
        this.simulated = simulated;
    }

    public Long getId() { return id; }
    public String getMessageType() { return messageType; }
    public String getSendStatus() { return sendStatus; }
    public String getCallbackStatus() { return callbackStatus; }
    public Instant getObservedAt() { return observedAt; }
    public String getDetail() { return detail; }
    public boolean isSimulated() { return simulated; }
}
