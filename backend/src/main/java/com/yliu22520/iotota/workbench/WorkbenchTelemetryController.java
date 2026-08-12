package com.yliu22520.iotota.workbench;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/diagnostic-tasks")
public class WorkbenchTelemetryController {

    private final WorkbenchTelemetryService service;

    public WorkbenchTelemetryController(WorkbenchTelemetryService service) {
        this.service = service;
    }

    @GetMapping("/{diagnosticTaskId}/telemetry")
    public WorkbenchDtos.Telemetry get(@PathVariable UUID diagnosticTaskId) {
        return service.get(diagnosticTaskId);
    }
}
