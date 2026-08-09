package com.yliu22520.iotota.diagnosis;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DiagnosticCreatedListener {

    private final DiagnosticAsyncExecutor executor;

    public DiagnosticCreatedListener(DiagnosticAsyncExecutor executor) {
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(DiagnosticCreatedEvent event) {
        executor.execute(event);
    }
}
