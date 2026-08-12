package com.yliu22520.iotota.diagnosis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnosticStateMachineTest {

    private final DiagnosticStateMachine stateMachine = new DiagnosticStateMachine();

    @Test
    void permitsTheReadOnlyDiagnosisWorkflow() {
        assertThat(stateMachine.transition(DiagnosticState.CREATED, DiagnosticState.INVESTIGATING))
                .isEqualTo(DiagnosticState.INVESTIGATING);
        assertThat(stateMachine.transition(DiagnosticState.INVESTIGATING, DiagnosticState.REPORT_READY))
                .isEqualTo(DiagnosticState.REPORT_READY);
        assertThat(stateMachine.transition(DiagnosticState.REPORT_READY, DiagnosticState.COMPLETED))
                .isEqualTo(DiagnosticState.COMPLETED);
    }

    @Test
    void permitsAnIncompleteDiagnosisFromInvestigation() {
        assertThat(stateMachine.transition(DiagnosticState.INVESTIGATING, DiagnosticState.INCOMPLETE))
                .isEqualTo(DiagnosticState.INCOMPLETE);
    }

    @Test
    void permitsTheApprovedRetryExecutionAndVerificationWorkflow() {
        assertThat(stateMachine.transition(DiagnosticState.REPORT_READY, DiagnosticState.WAITING_APPROVAL))
                .isEqualTo(DiagnosticState.WAITING_APPROVAL);
        assertThat(stateMachine.transition(DiagnosticState.WAITING_APPROVAL, DiagnosticState.EXECUTING))
                .isEqualTo(DiagnosticState.EXECUTING);
        assertThat(stateMachine.transition(DiagnosticState.EXECUTING, DiagnosticState.VERIFYING))
                .isEqualTo(DiagnosticState.VERIFYING);
        assertThat(stateMachine.transition(DiagnosticState.VERIFYING, DiagnosticState.COMPLETED))
                .isEqualTo(DiagnosticState.COMPLETED);
    }

    @Test
    void rejectsSkippingInvestigationAndChangingTerminalState() {
        assertThatThrownBy(() -> stateMachine.transition(DiagnosticState.CREATED, DiagnosticState.REPORT_READY))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> stateMachine.transition(DiagnosticState.COMPLETED, DiagnosticState.INVESTIGATING))
                .isInstanceOf(IllegalStateException.class);
    }
}
