package com.yliu22520.iotota.simulator;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Owns the database transaction so scheduled invocations cannot bypass the transactional boundary. */
@Service
public class DemoDataResetTransaction {

    static final List<String> RESET_TABLES = List.of(
            "simulator_retry_attempt",
            "retry_execution",
            "retry_plan",
            "diagnostic_report",
            "diagnostic_task",
            "audit_event",
            "message_state",
            "failure_log",
            "upgrade_task",
            "device",
            "firmware_version");

    private final JdbcTemplate jdbcTemplate;
    private final SimulatorDataInitializer simulatorDataInitializer;

    public DemoDataResetTransaction(JdbcTemplate jdbcTemplate,
                                    SimulatorDataInitializer simulatorDataInitializer) {
        this.jdbcTemplate = jdbcTemplate;
        this.simulatorDataInitializer = simulatorDataInitializer;
    }

    @Transactional
    public void resetNow() {
        jdbcTemplate.execute("truncate table " + String.join(", ", RESET_TABLES)
                + " restart identity");
        simulatorDataInitializer.seed();
    }
}
