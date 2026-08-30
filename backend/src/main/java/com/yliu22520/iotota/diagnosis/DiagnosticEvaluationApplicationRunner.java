package com.yliu22520.iotota.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** Explicit real-model evaluation entry point; it is only wired when evaluation is enabled. */
public final class DiagnosticEvaluationApplicationRunner implements ApplicationRunner {

    private final DiagnosticModelClient client;
    private final ObjectMapper objectMapper;
    private final Path outputDirectory;
    private final Clock clock;

    public DiagnosticEvaluationApplicationRunner(DiagnosticModelClient client,
                                                 ObjectMapper objectMapper,
                                                 Path outputDirectory,
                                                 Clock clock) {
        this.client = Objects.requireNonNull(client, "client");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        DiagnosticEvaluationReportWriter writer = new DiagnosticEvaluationReportWriter(objectMapper);
        for (DiagnosticEvaluationReport report : evaluate()) {
            writer.write(report, outputDirectory);
        }
    }

    List<DiagnosticEvaluationReport> evaluate() {
        DiagnosticEvaluationCaseRunner caseRunner = new RealModelEvaluationCaseRunner(client, clock, objectMapper);
        return new DiagnosticCandidateEvaluationRunner().run(caseRunner);
    }
}
