package com.company.workflowbuilder.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("workflow_migration_test")
            .withUsername("workflow")
            .withPassword("workflow");

    @Test
    void migratesAnEmptyDatabaseToTheCurrentSchema() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .outOfOrder(false)
                .load();

        assertThat(flyway.migrate().success).isTrue();
        flyway.validate();

        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet tables = statement.executeQuery("""
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN ('workflows', 'workflow_instances', 'request_drafts', 'password_reset_token', 'system_action_executions', 'workflow_batch_record_access')
                        """)) {
            assertThat(tables.next()).isTrue();
            assertThat(tables.getInt(1)).isEqualTo(6);
        }
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet columns = statement.executeQuery("""
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'workflow_batch_records'
                          AND column_name IN ('last_outcome', 'last_reason', 'last_actor_id', 'last_action_at', 'completed_at')
                        """)) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getInt(1)).isEqualTo(5);
        }
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet columns = statement.executeQuery("""
                        SELECT data_type FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'workflow_tasks'
                          AND column_name IN ('review_results', 'calculated_results')
                        """)) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getString(1)).isEqualTo("text");
            assertThat(columns.next()).isTrue();
            assertThat(columns.getString(1)).isEqualTo("text");
        }
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet columns = statement.executeQuery("""
                        SELECT data_type FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = 'system_action_executions'
                          AND column_name = 'response_selector_json'
                        """)) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getString(1)).isEqualTo("text");
        }
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet columns = statement.executeQuery("""
                        SELECT table_name, data_type FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND column_name = 'config_json'
                          AND table_name IN ('workflow_steps', 'data_connectors')
                        ORDER BY table_name
                        """)) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getString("table_name")).isEqualTo("data_connectors");
            assertThat(columns.getString("data_type")).isEqualTo("jsonb");
            assertThat(columns.next()).isTrue();
            assertThat(columns.getString("table_name")).isEqualTo("workflow_steps");
            assertThat(columns.getString("data_type")).isEqualTo("jsonb");
        }
    }
}
