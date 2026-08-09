package com.yliu22520.iotota.diagnosis;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class DiagnosticAsyncExecutor {

    private final DiagnosticWorkflow workflow;

    public DiagnosticAsyncExecutor(DiagnosticWorkflow workflow) {
        this.workflow = workflow;
    }

    @Async("diagnosticExecutor")
    public void execute(DiagnosticCreatedEvent event) {
        workflow.execute(event.diagnosticTaskId(), event.actor());
    }
}
