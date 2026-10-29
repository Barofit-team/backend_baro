package com.barofit.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    classes = ReportPostgresIntegrationTest.ReportTestApplication.class
)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/barofit_report_dev",
    "spring.datasource.username=postgres",
    "spring.datasource.password=postgres",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true"
})
class ReportPeriodIntegrationTest {

    @Autowired
    ReportService service;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper mapper;

    private void insert(
            UUID setId,
            UUID sessionId,
            UUID userId,
            String receivedAt) {

        jdbc.update(
            """
            INSERT INTO exercise_set_results (
                set_id, session_id, user_id, exercise,
                schema_version, engine_version,
                rep_count, observed_seconds,
                raw_result, received_at
            )
            VALUES (?, ?, ?, 'pushup', '2.0',
                    'period-test', 10, 30,
                    CAST('{}' AS jsonb), ?)
            """,
            setId, sessionId, userId,
            Timestamp.from(Instant.parse(receivedAt))
        );
    }

    private long number(Map<String, Object> result, String key) {
        return ((Number) result.get(key)).longValue();
    }

    @Test
    void dailySummaryUsesUtcBoundariesAndUserIsolation() {
        UUID user = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        UUID session = UUID.randomUUID();

        UUID before = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID last = UUID.randomUUID();
        UUID after = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        try {
            insert(before, session, user, "2026-10-25T23:59:59Z");
            insert(first, session, user, "2026-10-26T00:00:00Z");
            insert(last, session, user, "2026-10-26T23:59:59Z");
            insert(after, session, user, "2026-10-27T00:00:00Z");
            insert(other, session, otherUser, "2026-10-26T12:00:00Z");

            Map<String, Object> result =
                service.getDailySummary(
                    user, LocalDate.of(2026, 10, 26)
                );

            assertEquals(2, number(result, "setCount"));
            assertEquals(1, number(result, "sessionCount"));
            assertEquals(20, number(result, "totalReps"));
            assertEquals(
                60.0,
                ((Number) result.get("totalObservedSeconds")).doubleValue(),
                0.0001
            );

        } finally {
            for (UUID id : new UUID[] {
                    before, first, last, after, other
            }) {
                jdbc.update(
                    "DELETE FROM exercise_set_results WHERE set_id = ?",
                    id
                );
            }
        }
    }

    @Test
    void weeklySummaryCoversExactlySevenUtcDays() {
        UUID user = UUID.randomUUID();
        UUID session = UUID.randomUUID();

        UUID dayOne = UUID.randomUUID();
        UUID daySeven = UUID.randomUUID();
        UUID dayEight = UUID.randomUUID();

        try {
            insert(dayOne, session, user, "2026-10-26T00:00:00Z");
            insert(daySeven, session, user, "2026-11-01T23:59:59Z");
            insert(dayEight, session, user, "2026-11-02T00:00:00Z");

            Map<String, Object> result =
                service.getWeeklySummary(
                    user, LocalDate.of(2026, 10, 26)
                );

            assertEquals(2, number(result, "setCount"));
            assertEquals(20, number(result, "totalReps"));

        } finally {
            for (UUID id : new UUID[] {
                    dayOne, daySeven, dayEight
            }) {
                jdbc.update(
                    "DELETE FROM exercise_set_results WHERE set_id = ?",
                    id
                );
            }
        }
    }
}