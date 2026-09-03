package com.yliu22520.iotota.diagnosis;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestEmbeddingConfiguration.class)
class LiveDiagnosisQuotaIT {

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

    @MockitoBean
    private DiagnosticModelClient modelClient;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("knowledge.embedding.provider", () -> "test");
        registry.add("diagnosis.model.provider", () -> " GEMINI ");
        registry.add("demo.data-reset.enabled", () -> "false");
        registry.add("demo.live-diagnosis.daily-limit", () -> "20");
        registry.add("demo.live-diagnosis.per-minute-limit", () -> "1");
    }

    @BeforeEach
    void restoreKnownStateAndMakeTheExternalModelUnavailable() {
        resetService.resetNow();
        doThrow(new DiagnosticModelUnavailableException(DiagnosticModelUnavailableException.API_KEY_MISSING))
                .when(modelClient).complete(any(DiagnosticModelRequest.class));
    }

    @Test
    void quotaCountsModelBackedDiagnosisAcrossSessionsButNotDeterministicCallbackPath() throws Exception {
        MockHttpSession firstSession = login();
        MockHttpSession secondSession = login();
        String callbackTaskId = jdbcTemplate.queryForObject(
                "select id::text from upgrade_task where failure_code = 'CALLBACK_TIMEOUT' limit 1", String.class);
        String taskId = jdbcTemplate.queryForObject(
                "select id::text from upgrade_task where failure_code = 'VERSION_INCOMPATIBLE' limit 1", String.class);

        MvcResult callbackCreated = mockMvc.perform(post("/api/v1/diagnostic-tasks")
                        .with(csrf())
                        .session(firstSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upgradeTaskId\":\"" + callbackTaskId + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn();
        String callbackDiagnosticTaskId = JsonPath.read(
                callbackCreated.getResponse().getContentAsString(), "$.diagnosticTaskId");
        String callbackResult = waitForState(firstSession, callbackDiagnosticTaskId, "WAITING_APPROVAL");
        assertThat(callbackResult).contains("CALLBACK_TIMEOUT");
        assertThat((String) JsonPath.read(callbackResult, "$.report.retryPlan.status"))
                .isEqualTo("PENDING_APPROVAL");
        verify(modelClient, times(0)).complete(any(DiagnosticModelRequest.class));

        MvcResult created = mockMvc.perform(post("/api/v1/diagnostic-tasks")
                        .with(csrf())
                        .session(firstSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upgradeTaskId\":\"" + taskId + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn();
        String diagnosticTaskId = JsonPath.read(created.getResponse().getContentAsString(), "$.diagnosticTaskId");
        String incomplete = waitForState(firstSession, diagnosticTaskId, "INCOMPLETE");

        assertThat(incomplete).contains("EVIDENCE_INCOMPLETE", DiagnosticModelUnavailableException.API_KEY_MISSING);
        assertThat(incomplete).doesNotContain("controlled-diagnostic-explainer-v1", "reasoning_content");
        verify(modelClient, times(1)).complete(any(DiagnosticModelRequest.class));

        mockMvc.perform(get("/api/v1/diagnostic-tasks/{id}/telemetry", diagnosticTaskId)
                        .session(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelConfiguration.provider").value("gemini"))
                .andExpect(jsonPath("$.modelConfiguration.modelId").value("gemini-3.1-flash-lite"))
                .andExpect(jsonPath("$.modelConfiguration.automaticFallbackEnabled").value(false))
                .andExpect(jsonPath("$.tokenUsage.reported").value(false));

        mockMvc.perform(post("/api/v1/diagnostic-tasks")
                        .with(csrf())
                        .session(secondSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upgradeTaskId\":\"" + taskId + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.code").value("LIVE_DIAGNOSIS_RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());

        mockMvc.perform(get("/api/v1/public/diagnostic-summaries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
        assertThat(jdbcTemplate.queryForObject("select count(*) from diagnostic_task", Integer.class)).isEqualTo(2);
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

    private String waitForState(MockHttpSession session, String diagnosticTaskId, String expectedState)
            throws Exception {
        String response = "";
        String state = "";
        for (int attempt = 0; attempt < 100 && !expectedState.equals(state); attempt++) {
            Thread.sleep(100L);
            response = mockMvc.perform(get("/api/v1/diagnostic-tasks/{id}", diagnosticTaskId)
                            .session(session))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            state = JsonPath.read(response, "$.state");
        }
        assertThat(state).isEqualTo(expectedState);
        return response;
    }
}
