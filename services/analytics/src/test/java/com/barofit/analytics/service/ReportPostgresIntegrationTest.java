package com.barofit.analytics.service;

import com.barofit.analytics.repository.ReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ReportPostgresIntegrationTest.ReportTestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/barofit_report_dev",
    "spring.datasource.username=postgres",
    "spring.datasource.password=postgres",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true"
})
@Transactional
class ReportPostgresIntegrationTest {

    @Autowired
    private ReportService service;

    @Autowired
    private ReportRepository repository;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void savesReadsAndAggregatesRealPostgresData() throws Exception {

        UUID userId = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID set1 = UUID.randomUUID();
        UUID set2 = UUID.randomUUID();

        var payload1 = mapper.readTree("""
            {
              "schemaVersion": "2.0",
              "type": "set.completed",
              "exercise": "pushup",
              "engineVersion": "integration-test",
              "summary": {
                "schemaVersion": "2.0",
                "exercise": "pushup",
                "engineVersion": "integration-test",
                "count": 10,
                "holdSeconds": null,
                "observedSeconds": 30,
                "gapSeconds": 0,
                "items": {
                  "hip_alignment": {
                    "aggregationBasis": "repetition",
                    "unit": "repetitions",
                    "compliant": 9,
                    "warning": 1,
                    "unknown": 0,
                    "notApplicable": 0,
                    "evaluated": 10,
                    "compliancePct": 90
                  }
                }
              }
            }
            """);

        var payload2 = mapper.readTree("""
            {
              "schemaVersion": "2.0",
              "type": "set.completed",
              "exercise": "pushup",
              "engineVersion": "integration-test",
              "summary": {
                "schemaVersion": "2.0",
                "exercise": "pushup",
                "engineVersion": "integration-test",
                "count": 10,
                "holdSeconds": null,
                "observedSeconds": 30,
                "gapSeconds": 0,
                "items": {
                  "hip_alignment": {
                    "aggregationBasis": "repetition",
                    "unit": "repetitions",
                    "compliant": 1,
                    "warning": 9,
                    "unknown": 0,
                    "notApplicable": 0,
                    "evaluated": 10,
                    "compliancePct": 10
                  }
                }
              }
            }
            """);

        service.saveSet(userId, sessionId, set1, payload1);
        service.saveSet(userId, sessionId, set2, payload2);

        Integer savedSets = jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM exercise_set_results
            WHERE user_id = ? AND session_id = ?
            """,
            Integer.class,
            userId,
            sessionId
        );

        assertEquals(2, savedSets);

        Integer savedItems = jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM posture_item_results
            WHERE set_id IN (?, ?)
            """,
            Integer.class,
            set1,
            set2
        );

        assertEquals(2, savedItems);

        List<Map<String, Object>> sets =
            service.getSessionSets(userId, sessionId);

        assertEquals(2, sets.size());

        Map<String, Object> summary =
            service.getSessionSummary(userId, sessionId);

        assertEquals(2L, ((Number) summary.get("setCount")).longValue());
        assertEquals(20L, ((Number) summary.get("totalReps")).longValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
            (List<Map<String, Object>>) summary.get("postureItems");

        assertEquals(1, items.size());

        double accuracy =
            ((Number) items.get(0).get("compliancePct")).doubleValue();

        assertEquals(50.0, accuracy, 0.0001);

        assertEquals(
            0,
            service.getSessionSets(otherUser, sessionId).size()
        );

        Map<String, Object> otherSummary =
            service.getSessionSummary(otherUser, sessionId);

        assertEquals(
            0L,
            ((Number) otherSummary.get("setCount")).longValue()
        );
    }

    @Test
    void duplicateSetDoesNotCreateExtraRows() throws Exception {

        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();

        var payload = mapper.readTree("""
            {
              "schemaVersion": "2.0",
              "type": "set.completed",
              "exercise": "plank",
              "engineVersion": "integration-test",
              "summary": {
                "schemaVersion": "2.0",
                "exercise": "plank",
                "engineVersion": "integration-test",
                "count": null,
                "holdSeconds": 30,
                "observedSeconds": 35,
                "gapSeconds": 5,
                "items": {}
              }
            }
            """);

        service.saveSet(userId, sessionId, setId, payload);
        service.saveSet(userId, sessionId, setId, payload);

        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM exercise_set_results WHERE set_id = ?",
            Integer.class,
            setId
        );

        assertEquals(1, count);
    }

    @org.springframework.boot.autoconfigure.SpringBootApplication(
        scanBasePackages = "com.barofit.report.test.none"
    )
    @org.springframework.context.annotation.Import({
        ReportService.class,
        ReportRepository.class,
        com.barofit.analytics.config.ReportJacksonConfig.class
    })
    static class ReportTestApplication {
    }
}