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
                          AND table_name IN ('workflows', 'workflow_instances', 'request_drafts', 'password_reset_token')
                        """)) {
            assertThat(tables.next()).isTrue();
            assertThat(tables.getInt(1)).isEqualTo(4);
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
    }
}
