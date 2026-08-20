package com.yliu22520.iotota.simulator;

import com.yliu22520.iotota.TestEmbeddingConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Import(TestEmbeddingConfiguration.class)
class DemoDataResetIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("ota_operations")
            .withUsername("ota")
            .withPassword("ota");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DemoDataResetService resetService;

    @MockitoSpyBean
    private SimulatorDataInitializer simulatorDataInitializer;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("knowledge.embedding.provider", () -> "test");
        registry.add("demo.data-reset.enabled", () -> "true");
        registry.add("demo.data-reset.initial-delay-ms", () -> "86400000");
        registry.add("demo.data-reset.interval-ms", () -> "86400000");
    }

    @Test
    void resetSetMatchesFlywayTablesAndContainsEveryForeignKeyDependency() {
        List<String> actualTables = jdbcTemplate.queryForList(
                        "select table_name from information_schema.tables where table_schema = 'public'",
                        String.class)
                .stream()
                .filter(DemoDataResetTransaction.RESET_TABLES::contains)
                .toList();

        assertThat(actualTables).containsExactlyInAnyOrderElementsOf(DemoDataResetTransaction.RESET_TABLES);

        List<Map<String, Object>> foreignKeys = jdbcTemplate.queryForList("""
                select tc.table_name as child_table, ccu.table_name as parent_table
                from information_schema.table_constraints tc
                join information_schema.constraint_column_usage ccu
                  on ccu.constraint_name = tc.constraint_name
                 and ccu.constraint_schema = tc.constraint_schema
                where tc.table_schema = 'public'
                  and tc.constraint_type = 'FOREIGN KEY'
                """);
        for (Map<String, Object> foreignKey : foreignKeys) {
            String parentTable = String.valueOf(foreignKey.get("parent_table"));
            if (DemoDataResetTransaction.RESET_TABLES.contains(parentTable)) {
                assertThat(DemoDataResetTransaction.RESET_TABLES)
                        .contains(String.valueOf(foreignKey.get("child_table")));
            }
        }
    }

    @Test
    void resetRestoresKnownStateWithoutDeletingOperatorOrKnowledgeData() {
        jdbcTemplate.update("""
                insert into audit_event(id, occurred_at, actor, object_type, object_id, action, result,
                                        correlation_id, summary, metadata)
                values (?, current_timestamp, 'test', 'RESET_TEST', 'reset', 'TEST', 'RECORDED',
                        null, 'temporary reset evidence', null)
                """, UUID.randomUUID());

        resetService.resetNow();

        assertThat(count("device")).isEqualTo(2);
        assertThat(count("firmware_version")).isEqualTo(2);
        assertThat(count("upgrade_task")).isEqualTo(2);
        assertThat(count("failure_log")).isEqualTo(2);
        assertThat(count("message_state")).isEqualTo(2);
        assertThat(count("diagnostic_task")).isZero();
        assertThat(count("diagnostic_report")).isZero();
        assertThat(count("retry_plan")).isZero();
        assertThat(count("retry_execution")).isZero();
        assertThat(count("simulator_retry_attempt")).isZero();
        assertThat(count("audit_event")).isZero();
        assertThat(count("operator_user")).isEqualTo(1);
        assertThat(count("knowledge_document")).isEqualTo(8);
        assertThat(count("knowledge_chunk")).isGreaterThanOrEqualTo(8);
    }

    @Test
    void scheduledResetRollsBackTruncateWhenReseedingFails() {
        int deviceCount = count("device");
        int taskCount = count("upgrade_task");
        doThrow(new IllegalStateException("seed failure")).when(simulatorDataInitializer).seed();

        assertThatThrownBy(resetService::scheduledReset)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("seed failure");

        assertThat(count("device")).isEqualTo(deviceCount);
        assertThat(count("upgrade_task")).isEqualTo(taskCount);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }
}
