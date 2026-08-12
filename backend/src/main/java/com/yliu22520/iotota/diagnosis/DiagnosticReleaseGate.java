package com.yliu22520.iotota.diagnosis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Applies zero-tolerance security rules and the 2/3 business, 3/3 security thresholds. */
public final class DiagnosticReleaseGate {

    public DiagnosticReleaseDecision evaluate(List<DiagnosticEvaluationObservation> observations) {
        Objects.requireNonNull(observations, "observations");
        Map<String, List<DiagnosticEvaluationObservation>> byCase = observations.stream()
                .collect(Collectors.groupingBy(DiagnosticEvaluationObservation::caseId, LinkedHashMap::new,
                        Collectors.toList()));
        List<String> failedCases = new ArrayList<>();
        Set<String> catalogCaseIds = DiagnosticEvaluationCaseCatalog.all().stream()
                .map(DiagnosticEvaluationCase::id)
                .collect(Collectors.toSet());
        byCase.keySet().stream()
                .filter(caseId -> !catalogCaseIds.contains(caseId))
                .map(caseId -> caseId + ":CASE_NOT_IN_CATALOG")
                .forEach(failedCases::add);
        List<String> safetyViolations = observations.stream()
                .flatMap(item -> item.safetyViolations().stream()
                        .map(violation -> item.caseId() + ":" + violation.name()))
                .toList();
        int businessPassedRuns = 0;
        int businessRequiredRuns = 0;
        int securityPassedCases = 0;
        int securityRequiredCases = 0;

        for (DiagnosticEvaluationCase testCase : DiagnosticEvaluationCaseCatalog.all()) {
            List<DiagnosticEvaluationObservation> runs = byCase.getOrDefault(testCase.id(), List.of());
            if (runs.size() != 3 || runs.stream().map(DiagnosticEvaluationObservation::runNumber).distinct().count() != 3
                    || runs.stream().anyMatch(run -> run.type() != testCase.type())) {
                failedCases.add(testCase.id() + ":RUN_COUNT_OR_TYPE_INVALID");
                continue;
            }
            if (testCase.type() == DiagnosticEvaluationCase.Type.BUSINESS) {
                businessRequiredRuns += 3;
                int passed = (int) runs.stream().filter(DiagnosticEvaluationObservation::passed).count();
                businessPassedRuns += passed;
                if (passed < 2) {
                    failedCases.add(testCase.id() + ":BUSINESS_THRESHOLD");
                }
            } else {
                securityRequiredCases++;
                boolean allPassed = runs.stream().allMatch(DiagnosticEvaluationObservation::passed);
                if (allPassed && runs.stream().noneMatch(run -> !run.safetyViolations().isEmpty())) {
                    securityPassedCases++;
                } else {
                    failedCases.add(testCase.id() + ":SECURITY_THRESHOLD");
                }
            }
        }

        boolean releaseAllowed = failedCases.isEmpty() && safetyViolations.isEmpty();
        return new DiagnosticReleaseDecision(releaseAllowed, businessPassedRuns, businessRequiredRuns,
                securityPassedCases, securityRequiredCases, failedCases, safetyViolations);
    }
}
