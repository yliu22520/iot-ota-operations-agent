package com.yliu22520.iotota.workbench;

import com.yliu22520.iotota.TestEmbeddingConfiguration;
import com.yliu22520.iotota.simulator.DemoDataResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestEmbeddingConfiguration.class)
class PublicDemoSecurityIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("ota_operations")
            .withUsername("ota")
            .withPassword("ota");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DemoDataResetService resetService;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("knowledge.embedding.provider", () -> "test");
        registry.add("demo.data-reset.enabled", () -> "false");
    }

    @BeforeEach
    void restoreKnownState() {
        resetService.resetNow();
    }

    @Test
    void anonymousVisitorsCanReadOnlySafeSummariesAndCannotReachProtectedApis() throws Exception {
        String taskId = jdbcTemplate.queryForObject(
                "select id::text from upgrade_task where failure_code = 'VERSION_INCOMPATIBLE' limit 1", String.class);
        int diagnosesBefore = count("diagnostic_task");
        int reportsBefore = count("diagnostic_report");
        int plansBefore = count("retry_plan");
        int executionsBefore = count("retry_execution");
        int attemptsBefore = count("simulator_retry_attempt");
        int auditsBefore = count("audit_event");

        mockMvc.perform(get("/api/v1/public/diagnostic-summaries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].simulated").value(true))
                .andExpect(jsonPath("$.items[0].diagnosticTaskId").doesNotExist())
                .andExpect(content().string(not(containsString("reasoning_content"))))
                .andExpect(content().string(not(containsString("GEMINI_API_KEY"))))
                .andExpect(content().string(not(containsString("retry-plan:"))));

        mockMvc.perform(get("/api/v1/workbench/tasks"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/diagnostic-tasks/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/diagnostic-tasks/active").param("upgradeTaskId", taskId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/diagnostic-tasks/{id}/telemetry", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/diagnostic-tasks")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upgradeTaskId\":\"" + taskId + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/diagnostic-tasks/{id}/retry-plan/approve", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planVersion\":1,\"acknowledged\":true}"))
                .andExpect(status().isUnauthorized());

        assertThat(count("diagnostic_task")).isEqualTo(diagnosesBefore);
        assertThat(count("diagnostic_report")).isEqualTo(reportsBefore);
        assertThat(count("retry_plan")).isEqualTo(plansBefore);
        assertThat(count("retry_execution")).isEqualTo(executionsBefore);
        assertThat(count("simulator_retry_attempt")).isEqualTo(attemptsBefore);
        assertThat(count("audit_event")).isEqualTo(auditsBefore);
    }

    @Test
    void authenticatedStateChangesRequireCsrfAndDuplicateActiveRequestsAreRejected() throws Exception {
        MockHttpSession session = login();
        String taskId = jdbcTemplate.queryForObject(
                "select id::text from upgrade_task where failure_code = 'VERSION_INCOMPATIBLE' limit 1", String.class);

        mockMvc.perform(post("/api/v1/diagnostic-tasks")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upgradeTaskId\":\"" + taskId + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/diagnostic-tasks/{id}/retry-plan/approve", UUID.randomUUID())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planVersion\":1,\"acknowledged\":true}"))
                .andExpect(status().isForbidden());

        UUID activeDiagnosticTaskId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into diagnostic_task(id, upgrade_task_id, state, created_at, updated_at)
                values (?, ?, 'CREATED', current_timestamp, current_timestamp)
                """, activeDiagnosticTaskId, UUID.fromString(taskId));
        try {
            mockMvc.perform(post("/api/v1/diagnostic-tasks")
                            .with(csrf())
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"upgradeTaskId\":\"" + taskId + "\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DIAGNOSTIC_ALREADY_ACTIVE"));
        } finally {
            jdbcTemplate.update("delete from diagnostic_task where id = ?", activeDiagnosticTaskId);
        }
    }

    private MockHttpSession login() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo-operator\",\"password\":\"demo-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }
}
