package com.yliu22520.iotota;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DiagnosticWorkflowIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("ota_operations")
            .withUsername("ota")
            .withPassword("ota");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void startsAndPollsAVersionIncompatibleDiagnosisWithoutWritingUpgradeData() throws Exception {
        var session = login();
        MvcResult taskResult = mockMvc.perform(get("/api/v1/upgrade-tasks")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn();
        List<String> taskIds = JsonPath.read(taskResult.getResponse().getContentAsString(),
                "$.items[?(@.failureCode == 'VERSION_INCOMPATIBLE')].id");
        String taskId = taskIds.get(0);
        UUID taskUuid = UUID.fromString(taskId);

        int upgradeCount = jdbcTemplate.queryForObject("select count(*) from upgrade_task", Integer.class);
        int deviceCount = jdbcTemplate.queryForObject("select count(*) from device", Integer.class);
        int firmwareCount = jdbcTemplate.queryForObject("select count(*) from firmware_version", Integer.class);
        int failureLogCount = jdbcTemplate.queryForObject("select count(*) from failure_log", Integer.class);
        int messageStateCount = jdbcTemplate.queryForObject("select count(*) from message_state", Integer.class);

        MvcResult created = mockMvc.perform(post("/api/v1/diagnostic-tasks")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upgradeTaskId\":\"" + taskUuid + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.diagnosticTaskId").isNotEmpty())
                .andExpect(jsonPath("$.state").value("CREATED"))
                .andExpect(jsonPath("$.statusUrl").isNotEmpty())
                .andReturn();

        String diagnosticTaskId = JsonPath.read(created.getResponse().getContentAsString(), "$.diagnosticTaskId");
        String state = "CREATED";
        String response = "";
        for (int attempt = 0; attempt < 100 && !isTerminal(state); attempt++) {
            Thread.sleep(100L);
            response = mockMvc.perform(get("/api/v1/diagnostic-tasks/{id}", diagnosticTaskId)
                            .session(session))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            state = JsonPath.read(response, "$.state");
        }

        assertThat(state).isEqualTo("COMPLETED");
        assertThat(response).contains("VERSION_INCOMPATIBLE", "FORBIDDEN", "TARGET_MODEL_NOT_SUPPORTED");
        assertThat(response).doesNotContain("reasoning_content");
        assertThat(jdbcTemplate.queryForObject("select count(*) from upgrade_task", Integer.class))
                .isEqualTo(upgradeCount);
        assertThat(jdbcTemplate.queryForObject("select count(*) from device", Integer.class))
                .isEqualTo(deviceCount);
        assertThat(jdbcTemplate.queryForObject("select count(*) from firmware_version", Integer.class))
                .isEqualTo(firmwareCount);
        assertThat(jdbcTemplate.queryForObject("select count(*) from failure_log", Integer.class))
                .isEqualTo(failureLogCount);
        assertThat(jdbcTemplate.queryForObject("select count(*) from message_state", Integer.class))
                .isEqualTo(messageStateCount);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_event where object_id = ? and action = 'DIAGNOSTIC_CREATED'",
                Integer.class, diagnosticTaskId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_event where object_id = ? and action = 'TOOL_CALLED'",
                Integer.class, diagnosticTaskId)).isGreaterThanOrEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_event where object_id = ? and action = 'REPORT_GENERATED'",
                Integer.class, diagnosticTaskId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_event where object_id = ? and action = 'DIAGNOSTIC_COMPLETED'",
                Integer.class, diagnosticTaskId)).isEqualTo(1);
    }

    @Test
    void allowsOnlyOneActiveDiagnosisForAnUpgradeTask() throws Exception {
        var session = login();
        String taskId = jdbcTemplate.queryForObject(
                "select id::text from upgrade_task where failure_code = 'VERSION_INCOMPATIBLE' limit 1", String.class);
        UUID upgradeTaskId = UUID.fromString(taskId);
        UUID activeDiagnosticTaskId = UUID.randomUUID();
        insertDiagnosticTask(activeDiagnosticTaskId, upgradeTaskId, "CREATED");
        try {
            String request = "{\"upgradeTaskId\":\"" + taskId + "\"}";
            mockMvc.perform(post("/api/v1/diagnostic-tasks").with(csrf()).session(session)
                            .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DIAGNOSTIC_ALREADY_ACTIVE"));

            assertThatThrownBy(() -> insertDiagnosticTask(UUID.randomUUID(), upgradeTaskId, "INVESTIGATING"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.update("delete from diagnostic_task where id = ?", activeDiagnosticTaskId);
        }
    }

    private void insertDiagnosticTask(UUID diagnosticTaskId, UUID upgradeTaskId, String state) {
        jdbcTemplate.update("""
                insert into diagnostic_task(id, upgrade_task_id, state, created_at, updated_at)
                values (?, ?, ?, current_timestamp, current_timestamp)
                """, diagnosticTaskId, upgradeTaskId, state);
    }

    private org.springframework.mock.web.MockHttpSession login() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo-operator\",\"password\":\"demo-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);
    }

    private static boolean isTerminal(String state) {
        return "COMPLETED".equals(state) || "INCOMPLETE".equals(state);
    }
}
