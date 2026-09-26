package com.incidentmind;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationSqlTest {

    @Test
    @DisplayName("10. Flyway V1 schema migration contains all required tables, indexes, and constraints")
    void flywayMigrationScript_ValidStructure() throws Exception {
        InputStream is = getClass().getResourceAsStream("/db/migration/V1__create_incidentmind_schema.sql");
        assertThat(is).isNotNull();

        String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);

        // Verify sequence
        assertThat(sql).contains("CREATE SEQUENCE IF NOT EXISTS incident_key_seq");

        // Verify all 8 core tables
        List<String> requiredTables = List.of(
                "CREATE TABLE incidents",
                "CREATE TABLE investigations",
                "CREATE TABLE investigation_tasks",
                "CREATE TABLE agent_runs",
                "CREATE TABLE tool_calls",
                "CREATE TABLE evidence",
                "CREATE TABLE recovery_attempts",
                "CREATE TABLE audit_events"
        );

        for (String table : requiredTables) {
            assertThat(sql).contains(table);
        }

        // Verify key foreign key constraints
        assertThat(sql).contains("CONSTRAINT fk_investigations_incident FOREIGN KEY (incident_id) REFERENCES incidents(id)");
        assertThat(sql).contains("CONSTRAINT fk_tasks_investigation FOREIGN KEY (investigation_id) REFERENCES investigations(id)");
        assertThat(sql).contains("CONSTRAINT fk_agent_runs_task FOREIGN KEY (task_id) REFERENCES investigation_tasks(id)");
        assertThat(sql).contains("CONSTRAINT fk_tool_calls_agent_run FOREIGN KEY (agent_run_id) REFERENCES agent_runs(id)");
        assertThat(sql).contains("CONSTRAINT fk_evidence_investigation FOREIGN KEY (investigation_id) REFERENCES investigations(id)");
        assertThat(sql).contains("CONSTRAINT fk_recovery_investigation FOREIGN KEY (investigation_id) REFERENCES investigations(id)");
        assertThat(sql).contains("CONSTRAINT fk_audit_investigation FOREIGN KEY (investigation_id) REFERENCES investigations(id)");

        // Verify required indexes
        List<String> requiredIndexes = List.of(
                "idx_incidents_key",
                "idx_incidents_status",
                "idx_incidents_created_at",
                "idx_investigations_incident_id",
                "idx_investigations_status",
                "idx_tasks_investigation_id",
                "idx_tasks_parent_task_id",
                "idx_agent_runs_task_id",
                "idx_tool_calls_agent_run_id",
                "idx_evidence_investigation_id",
                "idx_recovery_investigation_id",
                "idx_audit_investigation_id",
                "idx_audit_correlation_id",
                "idx_audit_occurred_at"
        );

        for (String indexName : requiredIndexes) {
            assertThat(sql).contains(indexName);
        }

        // Verify JSONB columns
        assertThat(sql).contains("input_payload JSONB");
        assertThat(sql).contains("output_payload JSONB");
        assertThat(sql).contains("request_payload JSONB");
        assertThat(sql).contains("response_payload JSONB");
        assertThat(sql).contains("raw_data JSONB");
        assertThat(sql).contains("details JSONB");
        assertThat(sql).contains("event_data JSONB");

        // Verify check constraints
        assertThat(sql).contains("chk_incident_severity");
        assertThat(sql).contains("chk_incident_status");
        assertThat(sql).contains("chk_investigation_status");
        assertThat(sql).contains("chk_task_priority");
        assertThat(sql).contains("chk_task_status");
        assertThat(sql).contains("chk_agent_run_status");
        assertThat(sql).contains("chk_tool_call_status");
        assertThat(sql).contains("chk_recovery_type");
        assertThat(sql).contains("chk_audit_actor_type");
    }

    @Test
    @DisplayName("Flyway V2 schema migration relaxes agent_run_id NOT NULL constraint for direct tool invocations")
    void flywayV2MigrationScript_ValidStructure() throws Exception {
        InputStream is = getClass().getResourceAsStream("/db/migration/V2__allow_null_agent_run_in_tool_calls.sql");
        assertThat(is).isNotNull();

        String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        assertThat(sql).contains("ALTER TABLE tool_calls ALTER COLUMN agent_run_id DROP NOT NULL;");
    }

    @Test
    @DisplayName("Flyway V3 schema migration adds assigned_agent_type column to investigation_tasks")
    void flywayV3MigrationScript_ValidStructure() throws Exception {
        InputStream is = getClass().getResourceAsStream("/db/migration/V3__add_assigned_agent_type_to_tasks.sql");
        assertThat(is).isNotNull();

        String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        assertThat(sql).contains("ALTER TABLE investigation_tasks ADD COLUMN IF NOT EXISTS assigned_agent_type VARCHAR(100);");
    }
}
