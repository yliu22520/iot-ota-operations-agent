package com.yliu22520.iotota.workbench;

import com.yliu22520.iotota.IotOtaOperationsApplication;
import com.yliu22520.iotota.TestEmbeddingConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = IotOtaOperationsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestEmbeddingConfiguration.class)
class WorkbenchPublicApiIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("ota_operations")
            .withUsername("ota")
            .withPassword("ota");

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("knowledge.embedding.provider", () -> "test");
    }

    @Test
    void exposesOnlySafePreGeneratedSummariesToAnonymousVisitors() throws Exception {
        mockMvc.perform(get("/api/v1/public/diagnostic-summaries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].caseId").value("VERSION_INCOMPATIBLE"))
                .andExpect(jsonPath("$.items[1].caseId").value("CALLBACK_TIMEOUT"))
                .andExpect(jsonPath("$.items[0].conclusion").isNotEmpty())
                .andExpect(jsonPath("$.items[0].confidence").isNotEmpty())
                .andExpect(jsonPath("$.items[0].evidence").isArray())
                .andExpect(jsonPath("$.items[0].simulated").value(true))
                .andExpect(jsonPath("$.items[0].diagnosticTaskId").doesNotExist())
                .andExpect(content().string(not(containsString("reasoning_content"))))
                .andExpect(content().string(not(containsString("retry-plan:"))));
    }

    @Test
    void authenticatedWorkbenchListExposesAnExplicitDiagnosisStatus() throws Exception {
        mockMvc.perform(get("/api/v1/workbench/tasks"))
                .andExpect(status().isUnauthorized());

        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo-operator\",\"password\":\"demo-password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(get("/api/v1/workbench/tasks")
                        .session((MockHttpSession) login.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].deviceSerialNumber").isNotEmpty())
                .andExpect(jsonPath("$.items[0].failureCode").isNotEmpty())
                .andExpect(jsonPath("$.items[0].diagnosticStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.items[0].simulated").value(true));
    }

    @Test
    void authenticatedReportCarriesConfidenceAndLocatableEvidence() throws Exception {
        MockHttpSession session = login();
        MvcResult created = mockMvc.perform(post("/api/v1/diagnostic-tasks")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upgradeTaskId\":\"00000000-0000-0000-0000-000000000101\"}"))
                .andExpect(status().isAccepted())
                .andReturn();
        String diagnosticTaskId = JsonPath.read(created.getResponse().getContentAsString(), "$.diagnosticTaskId");

        String response = "";
        String state = "";
        for (int attempt = 0; attempt < 100 && !isTerminal(state); attempt++) {
            Thread.sleep(100L);
            response = mockMvc.perform(get("/api/v1/diagnostic-tasks/{id}", diagnosticTaskId)
                            .session(session))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            state = JsonPath.read(response, "$.state");
        }

        org.assertj.core.api.Assertions.assertThat(state).isEqualTo("COMPLETED");
        mockMvc.perform(get("/api/v1/diagnostic-tasks/{id}", diagnosticTaskId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report.confidence").value("HIGH"))
                .andExpect(jsonPath("$.report.evidenceRefs[0].kind").isNotEmpty())
                .andExpect(jsonPath("$.report.evidenceRefs[0].locator").isNotEmpty())
                .andExpect(jsonPath("$.report.evidenceRefs[0].source").isNotEmpty())
                .andExpect(jsonPath("$.report.evidenceRefs[0].observedAt").isNotEmpty());
    }

    @Test
    void exposesStructuredDeveloperTelemetryOnlyToAuthenticatedOperators() throws Exception {
        MockHttpSession session = login();
        MvcResult created = mockMvc.perform(post("/api/v1/diagnostic-tasks")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upgradeTaskId\":\"00000000-0000-0000-0000-000000000102\"}"))
                .andExpect(status().isAccepted())
                .andReturn();
        String diagnosticTaskId = JsonPath.read(created.getResponse().getContentAsString(), "$.diagnosticTaskId");
        waitForState(session, diagnosticTaskId, "WAITING_APPROVAL");

        mockMvc.perform(get("/api/v1/diagnostic-tasks/{id}/telemetry", diagnosticTaskId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/diagnostic-tasks/{id}/telemetry", diagnosticTaskId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosticTaskId").value(diagnosticTaskId))
                .andExpect(jsonPath("$.modelConfiguration.provider").value("controlled"))
                .andExpect(jsonPath("$.modelConfiguration.modelId").isNotEmpty())
                .andExpect(jsonPath("$.modelConfiguration.promptVersion").isNotEmpty())
                .andExpect(jsonPath("$.budget.maxToolCalls").value(10))
                .andExpect(jsonPath("$.budget.maxModelInteractions").value(8))
                .andExpect(jsonPath("$.toolEvents.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(6)))
                .andExpect(jsonPath("$.toolEvents[0].toolName").isNotEmpty())
                .andExpect(jsonPath("$.toolEvents[0].durationMs").isNumber())
                .andExpect(jsonPath("$.tokenUsage.reported").value(false))
                .andExpect(content().string(not(containsString("reasoning_content"))))
                .andExpect(content().string(not(containsString("hidden prompt"))));
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

    private static boolean isTerminal(String state) {
        return "COMPLETED".equals(state) || "INCOMPLETE".equals(state);
    }

    private void waitForState(MockHttpSession session, String diagnosticTaskId, String expectedState)
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
            if (isTerminal(state) && !expectedState.equals(state)) {
                break;
            }
        }
        org.assertj.core.api.Assertions.assertThat(state).isEqualTo(expectedState);
    }
}
