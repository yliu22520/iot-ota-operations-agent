package com.yliu22520.iotota.diagnosis;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnosticExecutionBudgetTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void v1LimitsMatchThePublishedDiagnosticBudget() {
        DiagnosticExecutionLimits limits = DiagnosticExecutionLimits.v1();

        assertThat(limits.maxToolCalls()).isEqualTo(10);
        assertThat(limits.maxModelInteractions()).isEqualTo(8);
        assertThat(limits.maxDuration()).isEqualTo(Duration.ofSeconds(90));
        assertThat(limits.maxOutputTokens()).isEqualTo(4_096);
        assertThat(limits.maxContextTokens()).isEqualTo(32_768);
        assertThat(limits.maxReadToolRetries()).isEqualTo(1);
    }

    @Test
    void rejectsWorkAfterTheToolBudgetIsExhausted() {
        DiagnosticExecutionBudget budget = new DiagnosticExecutionBudget(DiagnosticExecutionLimits.v1(), CLOCK);

        for (int index = 0; index < 10; index++) {
            budget.beforeToolCall();
        }

        assertThatThrownBy(budget::beforeToolCall)
                .isInstanceOf(DiagnosticBudgetExceededException.class)
                .hasMessageContaining("TOOL_CALL_LIMIT");
        assertThat(budget.toolCalls()).isEqualTo(10);
    }

    @Test
    void rejectsModelUsageAboveOutputOrContextLimits() {
        DiagnosticExecutionBudget budget = new DiagnosticExecutionBudget(DiagnosticExecutionLimits.v1(), CLOCK);

        assertThatThrownBy(() -> budget.recordModelUsage(32_768, 4_097))
                .isInstanceOf(DiagnosticBudgetExceededException.class)
                .hasMessageContaining("MODEL_OUTPUT_LIMIT");

        assertThatThrownBy(() -> budget.recordModelUsage(32_769, 1))
                .isInstanceOf(DiagnosticBudgetExceededException.class)
                .hasMessageContaining("MODEL_CONTEXT_LIMIT");
    }

    @Test
    void accumulatesTokenTelemetryAcrossModelInteractions() {
        DiagnosticExecutionBudget budget = new DiagnosticExecutionBudget(DiagnosticExecutionLimits.v1(), CLOCK);

        budget.recordModelUsage(100, 50);
        budget.recordModelUsage(200, 75);

        assertThat(budget.inputTokens()).isEqualTo(300);
        assertThat(budget.outputTokens()).isEqualTo(125);
    }
}
