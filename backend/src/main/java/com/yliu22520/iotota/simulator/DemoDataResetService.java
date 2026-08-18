package com.yliu22520.iotota.simulator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoDataResetService {

    private static final String RESET_SQL = """
            truncate table
                simulator_retry_attempt,
                retry_execution,
                retry_plan,
                diagnostic_report,
                diagnostic_task,
                audit_event,
                message_state,
                failure_log,
                upgrade_task,
                device,
                firmware_version
            restart identity cascade
            """;

    private final JdbcTemplate jdbcTemplate;
    private final SimulatorDataInitializer simulatorDataInitializer;
    private final boolean enabled;

    public DemoDataResetService(JdbcTemplate jdbcTemplate,
                                SimulatorDataInitializer simulatorDataInitializer,
                                @Value("${demo.data-reset.enabled:true}") boolean enabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.simulatorDataInitializer = simulatorDataInitializer;
        this.enabled = enabled;
    }

    @Scheduled(
            fixedDelayString = "${demo.data-reset.interval-ms:86400000}",
            initialDelayString = "${demo.data-reset.initial-delay-ms:86400000}")
    public void scheduledReset() {
        if (enabled) {
            resetNow();
        }
    }

    @Transactional
    public void resetNow() {
        jdbcTemplate.execute(RESET_SQL);
        simulatorDataInitializer.seed();
    }
}
