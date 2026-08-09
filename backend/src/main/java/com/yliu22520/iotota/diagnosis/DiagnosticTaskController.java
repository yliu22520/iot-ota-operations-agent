package com.yliu22520.iotota.diagnosis;

import com.yliu22520.iotota.simulator.UpgradeTaskNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/diagnostic-tasks")
public class DiagnosticTaskController {

    private final DiagnosticTaskService service;

    public DiagnosticTaskController(DiagnosticTaskService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DiagnosticApiModels.StartResponse> start(@RequestBody StartRequest request,
                                                                    Authentication authentication) {
        DiagnosticApiModels.StartResponse response = service.start(request.upgradeTaskId(),
                authentication.getName());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}")
    public DiagnosticApiModels.TaskView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/active")
    public ResponseEntity<DiagnosticApiModels.TaskView> active(@RequestParam UUID upgradeTaskId) {
        return service.findActiveForUpgradeTask(upgradeTaskId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(DiagnosticAlreadyActiveException.class)
    ResponseEntity<ProblemDetail> handleAlreadyActive(DiagnosticAlreadyActiveException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setProperty("code", "DIAGNOSTIC_ALREADY_ACTIVE");
        if (exception.getActiveDiagnosticTaskId() != null) {
            problem.setProperty("diagnosticTaskId", exception.getActiveDiagnosticTaskId());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler({DiagnosticTaskNotFoundException.class, UpgradeTaskNotFoundException.class})
    ResponseEntity<ProblemDetail> handleNotFound(RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setProperty("code", "DIAGNOSTIC_TARGET_NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleUniqueActiveViolation(DataIntegrityViolationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "An active diagnosis already exists for this upgrade task");
        problem.setProperty("code", "DIAGNOSTIC_ALREADY_ACTIVE");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    public record StartRequest(UUID upgradeTaskId) {
    }
}
