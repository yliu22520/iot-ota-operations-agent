package com.yliu22520.iotota.diagnosis;

import java.util.Map;
import java.util.Set;

/** Explicit state machine for persisted diagnosis tasks. */
public class DiagnosticStateMachine {

    private static final Map<DiagnosticState, Set<DiagnosticState>> TRANSITIONS = Map.of(
            DiagnosticState.CREATED, Set.of(DiagnosticState.INVESTIGATING),
            DiagnosticState.INVESTIGATING, Set.of(DiagnosticState.REPORT_READY, DiagnosticState.INCOMPLETE),
            DiagnosticState.REPORT_READY, Set.of(DiagnosticState.WAITING_APPROVAL, DiagnosticState.COMPLETED),
            DiagnosticState.WAITING_APPROVAL, Set.of(DiagnosticState.EXECUTING, DiagnosticState.INCOMPLETE),
            DiagnosticState.EXECUTING, Set.of(DiagnosticState.VERIFYING, DiagnosticState.INCOMPLETE),
            DiagnosticState.VERIFYING, Set.of(DiagnosticState.COMPLETED, DiagnosticState.INCOMPLETE),
            DiagnosticState.COMPLETED, Set.of(),
            DiagnosticState.INCOMPLETE, Set.of());

    public DiagnosticState transition(DiagnosticState current, DiagnosticState next) {
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new IllegalStateException("Illegal diagnostic state transition: " + current + " -> " + next);
        }
        return next;
    }
}
