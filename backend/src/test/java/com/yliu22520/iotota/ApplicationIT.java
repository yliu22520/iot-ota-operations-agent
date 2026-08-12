package com.yliu22520.iotota;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.flywaydb.core.Flyway;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationIT {

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
    private Flyway flyway;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void migratesAndSeedsTheTwoRequiredFailureSamples() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('device', 'firmware_version', 'upgrade_task', 'failure_log',
                                     'message_state', 'operator_user', 'diagnostic_task', 'diagnostic_report',
                                     'retry_plan', 'retry_execution', 'simulator_retry_attempt',
                                     'knowledge_document', 'audit_event')
                """, Integer.class);

        assertThat(tableCount).isEqualTo(13);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
        assertThat(jdbcTemplate.queryForObject("select count(*) from pg_extension where extname = 'vector'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from upgrade_task", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select count(*) from failure_log", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select count(*) from message_state", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select count(*) from operator_user", Integer.class)).isEqualTo(1);
    }

    @Test
    void protectsTasksWithSessionAuthenticationAndReturnsSimulatedFailureDetails() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mockMvc.perform(get("/api/v1/upgrade-tasks"))
                .andExpect(status().isUnauthorized());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"demo-operator","password":"demo-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("demo-operator"))
                .andReturn();

        var session = login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        MvcResult tasks = mockMvc.perform(get("/api/v1/upgrade-tasks")
                        .session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[?(@.failureCode == 'VERSION_INCOMPATIBLE')].id").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.failureCode == 'CALLBACK_TIMEOUT')].id").isNotEmpty())
                .andReturn();

        String taskId = com.jayway.jsonpath.JsonPath.read(tasks.getResponse().getContentAsString(), "$.items[0].id");
        mockMvc.perform(get("/api/v1/upgrade-tasks/{id}", taskId)
                        .session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.task.id").value(taskId))
                .andExpect(jsonPath("$.device.id").isNotEmpty())
                .andExpect(jsonPath("$.firmwareVersion.id").isNotEmpty())
                .andExpect(jsonPath("$.failureLogs.length()").value(1))
                .andExpect(jsonPath("$.messageStates.length()").value(1));
    }

    @Test
    void supportsSessionLogout() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo-operator\",\"password\":\"demo-password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var session = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/upgrade-tasks").session(session))
                .andExpect(status().isUnauthorized());
    }
}
