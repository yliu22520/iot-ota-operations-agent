package com.yliu22520.iotota.simulator;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/upgrade-tasks")
public class SimulatorController {

    private final SimulatorService simulatorService;

    public SimulatorController(SimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    @GetMapping
    public SimulatorDtos.TaskList list(@RequestParam(defaultValue = "FINAL_FAILURE") String status) {
        if (!UpgradeTaskStatus.FINAL_FAILURE.name().equals(status)) {
            return new SimulatorDtos.TaskList(java.util.List.of(), true);
        }
        return simulatorService.listFinalFailureTasks();
    }

    @GetMapping("/{id}")
    public SimulatorDtos.TaskDetail detail(@PathVariable UUID id) {
        return simulatorService.getTask(id);
    }

    @ExceptionHandler(UpgradeTaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleTaskNotFound(UpgradeTaskNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setProperty("code", "UPGRADE_TASK_NOT_FOUND");
        return problem;
    }
}
