package com.yliu22520.iotota.simulator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DemoDataResetService {

    private final DemoDataResetTransaction resetTransaction;
    private final boolean enabled;

    public DemoDataResetService(DemoDataResetTransaction resetTransaction,
                                @Value("${demo.data-reset.enabled:true}") boolean enabled) {
        this.resetTransaction = resetTransaction;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString = "${demo.data-reset.interval-ms:86400000}",
            initialDelayString = "${demo.data-reset.initial-delay-ms:86400000}")
    public void scheduledReset() {
        if (enabled) {
            resetNow();
        }
    }

    public void resetNow() {
        resetTransaction.resetNow();
    }
}
