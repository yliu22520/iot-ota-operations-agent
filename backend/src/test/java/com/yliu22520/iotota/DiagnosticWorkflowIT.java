package com.yliu22520.iotota;

import com.jayway.jsonpath.JsonPath;
import com.yliu22520.iotota.action.RetryAuthorizationOutcome;
import com.yliu22520.iotota.action.RetryAuthorizationTransaction;
import com.yliu22520.iotota.action.RetryRecoveryService;
import com.yliu22520.iotota.knowledge.KnowledgeIndexInitializer;
import com.yliu22520.iotota.simulator.SimulatorRetryGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
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
@SpringBootTest(classes = IotOtaOperationsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestEmbeddingConfiguration.class)
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

    @Autowired
    private RetryAuthorizationTransaction retryAuthorizationTransaction;

    @Autowired
    private RetryRecoveryService retryRecoveryService;

    @Autowired
    private SimulatorRetryGateway simulatorRetryGateway;

    @Autowired
    private KnowledgeIndexInitializer knowledgeIndexInitializer;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("knowledge.embedding.provider", () -> "test");
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
        assertThat(response).contains("VERSION_INCOMPATIBLE", "FORBIDDEN", "TARGET_MODEL_NOT_SUPPORTED",
                "KNOWLEDGE_SUGGESTION", "knowledge:ota-version-compatibility");
        assertThat(JsonPath.<List<String>>read(response, "$.report.rootCauseEvidenceRefs"))
                .noneMatch(ref -> ref.startsWith("knowledge:"));
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
                Integer.class, diagnosticTaskId)).isGreaterThanOrEqualTo(6);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_event where object_id = ? and action = 'TOOL_CALLED' "
                        + "and metadata ->> 'toolName' = 'searchKnowledge'",
                Integer.class, diagnosticTaskId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_event where object_id = ? and action = 'REPORT_GENERATED'",
                Integer.class, diagnosticTaskId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_event where object_id = ? and action = 'DIAGNOSTIC_COMPLETED'",
                Integer.class, diagnosticTaskId)).isEqualTo(1);
    }

    @Test
    void completesWithExplicitGapsWhenLocalKnowledgeIsEmptyOrUnavailable() throws Exception {
        var session = login();
        String taskId = jdbcTemplate.queryForObject(
                "select id::text from upgrade_task where failure_code = 'VERSION_INCOMPATIBLE' limit 1", String.class);
        int upgradeVersion = jdbcTemplate.queryForObject(
                "select version from upgrade_task where id = ?", Integer.class, UUID.fromString(taskId));
        int retryAttempts = jdbcTemplate.queryForObject("select count(*) from simulator_retry_attempt", Integer.class);

        jdbcTemplate.update("delete from knowledge_chunk");
        String emptyResponse = startDiagnosisAndWait(session, taskId, "COMPLETED");
        assertThat(emptyResponse).contains("KNOWLEDGE_NOT_FOUND", "FORBIDDEN");
        assertThat(emptyResponse).doesNotContain("KNOWLEDGE_SUGGESTION");
        knowledgeIndexInitializer.initialize();

        boolean renamed = false;
        try {
            jdbcTemplate.execute("alter table knowledge_chunk rename to knowledge_chunk_unavailable");
            renamed = true;
            String response = startDiagnosisAndWait(session, taskId, "COMPLETED");
            String diagnosticTaskId = JsonPath.read(response, "$.diagnosticTaskId");

            assertThat(response).contains("KNOWLEDGE_RETRIEVAL_FAILED", "FORBIDDEN");
            assertThat(response).doesNotContain("KNOWLEDGE_SUGGESTION");
            assertThat(JsonPath.<Boolean>read(response, "$.report.retryEligibility.eligible")).isFalse();
            assertThat(jdbcTemplate.queryForObject(
                    "select version from upgrade_task where id = ?", Integer.class, UUID.fromString(taskId)))
                    .isEqualTo(upgradeVersion);
            assertThat(jdbcTemplate.queryForObject("select count(*) from simulator_retry_attempt", Integer.class))
                    .isEqualTo(retryAttempts);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from audit_event where object_id = ? and action = 'TOOL_CALLED' "
                            + "and result = 'FAILED' and metadata ->> 'toolName' = 'searchKnowledge'",
                    Integer.class, diagnosticTaskId)).isEqualTo(1);
        } finally {
            if (renamed) {
                jdbcTemplate.execute("alter table knowledge_chunk_unavailable rename to knowledge_chunk");
            }
        }
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

    @Test
    void callbackTimeoutDiagnosisProducesAnEvidenceBoundFifteenMinuteRetryPlan() throws Exception {
        var session = login();
        String taskId = jdbcTemplate.queryForObject(
                "select id::text from upgrade_task where failure_code = 'CALLBACK_TIMEOUT' limit 1", String.class);

        MvcResult created = mockMvc.perform(post("/api/v1/diagnostic-tasks")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upgradeTaskId\":\"" + taskId + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn();

        String diagnosticTaskId = JsonPath.read(created.getResponse().getContentAsString(), "$.diagnosticTaskId");
        String response = waitForState(session, diagnosticTaskId, "WAITING_APPROVAL");

        assertThat(response).contains("CALLBACK_TIMEOUT", "CALLBACK_TIMEOUT_RETRY_ALLOWED", "ELIGIBLE");
        assertThat(JsonPath.<Boolean>read(response, "$.report.retryEligibility.eligible")).isTrue();
        assertThat(JsonPath.<String>read(response, "$.report.retryPlan.upgradeTaskId")).isEqualTo(taskId);
        assertThat(JsonPath.<Number>read(response, "$.report.retryPlan.planVersion").intValue()).isEqualTo(1);
        assertThat(JsonPath.<String>read(response, "$.report.retryPlan.expiresAt")).isNotBlank();
        assertThat(JsonPath.<List<String>>read(response, "$.report.retryPlan.preconditions"))
                .contains("TASK_FINAL_FAILURE", "DEVICE_ONLINE", "VERSION_COMPATIBLE", "CALLBACK_TIMEOUT_CONFIRMED");
        assertThat(JsonPath.<List<String>>read(response, "$.report.rootCauseEvidenceRefs"))
                .anyMatch(ref -> ref.startsWith("message-state:"))
                .anyMatch(ref -> ref.startsWith("failure-logs:"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from retry_plan where diagnostic_task_id = ?",
                Integer.class, UUID.fromString(diagnosticTaskId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from retry_execution", Integer.class)).isZero();
        jdbcTemplate.update("update diagnostic_task set state = 'INCOMPLETE' where id = ?",
                UUID.fromString(diagnosticTaskId));
    }

    @Test
    void approvalImmediatelyExecutesAndVerifiesExactlyOneSimulatorRetry() throws Exception {
        var session = login();
        String diagnosticTaskId = startCallbackTimeoutDiagnosis(session);
        waitForState(session, diagnosticTaskId, "WAITING_APPROVAL");

        MvcResult approved = mockMvc.perform(post("/api/v1/diagnostic-tasks/{id}/retry-plan/approve",
                        diagnosticTaskId)
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planVersion\":1,\"acknowledged\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diagnosticState").value("COMPLETED"))
                .andExpect(jsonPath("$.executionStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.verificationStatus").value("BUSINESS_ACCEPTED"))
                .andReturn();
        String executionId = JsonPath.read(approved.getResponse().getContentAsString(), "$.executionId");

        mockMvc.perform(post("/api/v1/diagnostic-tasks/{id}/retry-plan/approve", diagnosticTaskId)
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planVersion\":1,\"acknowledged\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId").value(executionId))
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from retry_execution re join retry_plan rp on rp.id = re.retry_plan_id "
                        + "where rp.diagnostic_task_id = ?", Integer.class, UUID.fromString(diagnosticTaskId)))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from simulator_retry_attempt where diagnostic_task_id = ?",
                Integer.class, UUID.fromString(diagnosticTaskId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_event where correlation_id = ? and action = 'RETRY_APPROVED'",
                Integer.class, diagnosticTaskId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_event where correlation_id = ? and action = 'RETRY_VERIFIED'",
                Integer.class, diagnosticTaskId)).isEqualTo(1);
    }

    @Test
    void expiredOrChangedPlanIsRejectedAndAuditedWithoutCallingTheSimulator() throws Exception {
        var session = login();
        String expiredDiagnosticId = startCallbackTimeoutDiagnosis(session);
        waitForState(session, expiredDiagnosticId, "WAITING_APPROVAL");
        jdbcTemplate.update("update retry_plan set expires_at = current_timestamp - interval '1 second' "
                + "where diagnostic_task_id = ?", UUID.fromString(expiredDiagnosticId));

        mockMvc.perform(post("/api/v1/diagnostic-tasks/{id}/retry-plan/approve", expiredDiagnosticId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planVersion\":1,\"acknowledged\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETRY_PLAN_EXPIRED"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from simulator_retry_attempt where diagnostic_task_id = ?", Integer.class,
                UUID.fromString(expiredDiagnosticId))).isZero();

        String changedDiagnosticId = startCallbackTimeoutDiagnosis(session);
        String changedResponse = waitForState(session, changedDiagnosticId, "WAITING_APPROVAL");
        String upgradeTaskId = JsonPath.read(changedResponse, "$.upgradeTaskId");
        jdbcTemplate.update("update upgrade_task set version = version + 1 where id = ?",
                UUID.fromString(upgradeTaskId));

        mockMvc.perform(post("/api/v1/diagnostic-tasks/{id}/retry-plan/approve", changedDiagnosticId)
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planVersion\":1,\"acknowledged\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETRY_PLAN_SNAPSHOT_CHANGED"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from simulator_retry_attempt where diagnostic_task_id = ?", Integer.class,
                UUID.fromString(changedDiagnosticId))).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_event where correlation_id in (?, ?) and action = 'RETRY_REJECTED'",
                Integer.class, expiredDiagnosticId, changedDiagnosticId)).isEqualTo(2);
    }

    @Test
    void noUnapprovedExecutionEndpointIsReachable() throws Exception {
        var session = login();
        mockMvc.perform(post("/api/v1/retry-executions")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upgradeTaskId\":\"00000000-0000-0000-0000-000000000102\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void recoversAnAcceptedSimulatorCallWithoutIssuingASecondAttempt() throws Exception {
        var session = login();
        String diagnosticTaskId = startCallbackTimeoutDiagnosis(session);
        waitForState(session, diagnosticTaskId, "WAITING_APPROVAL");
        UUID diagnosticId = UUID.fromString(diagnosticTaskId);
        UUID upgradeTaskId = UUID.fromString(jdbcTemplate.queryForObject(
                "select upgrade_task_id::text from diagnostic_task where id = ?", String.class, diagnosticId));
        String targetVersion = jdbcTemplate.queryForObject(
                "select fv.version from upgrade_task ut join firmware_version fv "
                        + "on fv.id = ut.target_firmware_version_id where ut.id = ?",
                String.class, upgradeTaskId);

        RetryAuthorizationOutcome authorized = retryAuthorizationTransaction.authorize(
                diagnosticId, 1, true, "demo-operator");
        assertThat(authorized.newExecution()).isTrue();
        simulatorRetryGateway.submit(diagnosticId, upgradeTaskId, targetVersion,
                authorized.execution().getIdempotencyKey());

        retryRecoveryService.reconcilePending();
        retryRecoveryService.reconcilePending();

        assertThat(jdbcTemplate.queryForObject(
                "select status from retry_execution where id = ?", String.class,
                authorized.execution().getId())).isEqualTo("VERIFIED");
        assertThat(jdbcTemplate.queryForObject(
                "select state from diagnostic_task where id = ?", String.class, diagnosticId))
                .isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from simulator_retry_attempt where diagnostic_task_id = ?",
                Integer.class, diagnosticId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_event where correlation_id = ? and action = 'RETRY_VERIFIED'",
                Integer.class, diagnosticTaskId)).isEqualTo(1);
    }

    @Test
    void marksVerificationIncompleteWhenRecoveryCannotFindSimulatorFact() throws Exception {
        var session = login();
        String diagnosticTaskId = startCallbackTimeoutDiagnosis(session);
        waitForState(session, diagnosticTaskId, "WAITING_APPROVAL");
        UUID diagnosticId = UUID.fromString(diagnosticTaskId);

        RetryAuthorizationOutcome authorized = retryAuthorizationTransaction.authorize(
                diagnosticId, 1, true, "demo-operator");
        assertThat(authorized.newExecution()).isTrue();

        retryRecoveryService.reconcilePending();

        assertThat(jdbcTemplate.queryForObject(
                "select status from retry_execution where id = ?", String.class,
                authorized.execution().getId())).isEqualTo("INCOMPLETE");
        assertThat(jdbcTemplate.queryForObject(
                "select state from diagnostic_task where id = ?", String.class, diagnosticId))
                .isEqualTo("INCOMPLETE");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from simulator_retry_attempt where diagnostic_task_id = ?",
                Integer.class, diagnosticId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_event where correlation_id = ? and action = 'RETRY_VERIFICATION_INCOMPLETE'",
                Integer.class, diagnosticTaskId)).isEqualTo(1);
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

    private String waitForState(org.springframework.mock.web.MockHttpSession session,
                                String diagnosticTaskId,
                                String expectedState) throws Exception {
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
        assertThat(state).isEqualTo(expectedState);
        return response;
    }

    private String startCallbackTimeoutDiagnosis(org.springframework.mock.web.MockHttpSession session) throws Exception {
        String taskId = jdbcTemplate.queryForObject(
                "select id::text from upgrade_task where failure_code = 'CALLBACK_TIMEOUT' limit 1", String.class);
        MvcResult created = mockMvc.perform(post("/api/v1/diagnostic-tasks")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upgradeTaskId\":\"" + taskId + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.diagnosticTaskId");
    }

    private String startDiagnosisAndWait(org.springframework.mock.web.MockHttpSession session,
                                         String upgradeTaskId,
                                         String expectedState) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/diagnostic-tasks")
                        .with(csrf()).session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"upgradeTaskId\":\"" + upgradeTaskId + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn();
        String diagnosticTaskId = JsonPath.read(created.getResponse().getContentAsString(), "$.diagnosticTaskId");
        return waitForState(session, diagnosticTaskId, expectedState);
    }

    private static boolean isTerminal(String state) {
        return "COMPLETED".equals(state) || "INCOMPLETE".equals(state);
    }
}
