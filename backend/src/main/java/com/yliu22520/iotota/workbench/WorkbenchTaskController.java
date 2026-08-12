package com.yliu22520.iotota.workbench;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workbench")
public class WorkbenchTaskController {

    private final WorkbenchTaskService service;

    public WorkbenchTaskController(WorkbenchTaskService service) {
        this.service = service;
    }

    @GetMapping("/tasks")
    public WorkbenchDtos.TaskList listTasks() {
        return service.listFinalFailureTasks();
    }
}
