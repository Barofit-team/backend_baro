package com.barofit.report;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ReportSchemaMigrationTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6");

    @Test
    void migrationCopiesExistingReportDataWithoutDeletingOriginal() throws Exception {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas("report").locations("classpath:db/migration/report").target("1").load().migrate();
        UUID set = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA analytics");
            statement.execute("CREATE TABLE analytics.exercise_set_results AS SELECT * FROM report.exercise_set_results WITH NO DATA");
            statement.execute("CREATE TABLE analytics.posture_item_results AS SELECT * FROM report.posture_item_results WITH NO DATA");
            try (var insert = connection.prepareStatement("""
                INSERT INTO analytics.exercise_set_results(set_id,session_id,user_id,exercise,schema_version,engine_version,raw_result,received_at)
                VALUES (?,?,?,'pushup','2.0','test','{}'::jsonb,now())
                """)) {
                insert.setObject(1, set); insert.setObject(2, UUID.randomUUID()); insert.setObject(3, UUID.randomUUID());
                insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement("""
                INSERT INTO analytics.posture_item_results VALUES (?,'alignment','repetition','repetitions',8,2,0,0,10,80)
                """)) { insert.setObject(1, set); insert.executeUpdate(); }
            statement.execute("DROP SCHEMA report CASCADE");
        }
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas("report").locations("classpath:db/migration/report").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement();
             var rs = statement.executeQuery("""
                SELECT (SELECT count(*) FROM analytics.exercise_set_results),
                       (SELECT count(*) FROM report.exercise_set_results),
                       (SELECT count(*) FROM report.posture_item_results)
                """)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
            assertThat(rs.getInt(2)).isEqualTo(1);
            assertThat(rs.getInt(3)).isEqualTo(1);
        }
    }
}
