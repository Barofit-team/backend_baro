package com.barofit.report.service;

import com.barofit.report.repository.ReportRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
class ReportConcurrencyIntegrationTest {

    @Autowired
    ReportService service;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper mapper;

    private JsonNode payload(boolean valid) throws Exception {
        String json = """
            {
              "schemaVersion": "2.0",
              "type": "set.completed",
              "exercise": "pushup",
              "engineVersion": "concurrency-test",
              "summary": {
                "schemaVersion": "2.0",
                "exercise": "pushup",
                "engineVersion": "concurrency-test",
                "count": 10,
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
            """;

        JsonNode node = mapper.readTree(json);

        if (!valid) {
            ((com.fasterxml.jackson.databind.node.ObjectNode)
                node.path("summary")
                    .path("items")
                    .path("hip_alignment"))
                .put("evaluated", 99);
        }

        return node;
    }

    private int countSets(UUID setId) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM exercise_set_results WHERE set_id = ?",
            Integer.class,
            setId
        );
    }

    private int countItems(UUID setId) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM posture_item_results WHERE set_id = ?",
            Integer.class,
            setId
        );
    }

    private void cleanup(UUID setId) {
        jdbc.update(
            "DELETE FROM exercise_set_results WHERE set_id = ?",
            setId
        );
    }

    @Test
    void concurrentSameSetIdCreatesOnlyOneRecord() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();

        JsonNode data = payload(true);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            java.util.concurrent.Callable<String> action = () -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start timeout");
                }

                try {
                    return (String) service.saveSet(
                        userId, sessionId, setId, data
                    ).get("status");
                } catch (ResponseStatusException ex) {
                    if (ex.getStatusCode().value() == 409) {
                        return "conflict";
                    }
                    throw ex;
                }
            };

            Future<String> first = pool.submit(action);
            Future<String> second = pool.submit(action);

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            String result1 = first.get(20, TimeUnit.SECONDS);
            String result2 = second.get(20, TimeUnit.SECONDS);

            assertTrue(
                result1.equals("saved")
                    || result1.equals("already_saved")
                    || result1.equals("conflict")
            );
            assertTrue(
                result2.equals("saved")
                    || result2.equals("already_saved")
                    || result2.equals("conflict")
            );

            assertTrue(
                result1.equals("saved") || result2.equals("saved"),
                "At least one request must save the record"
            );

            assertEquals(1, countSets(setId));
            assertEquals(1, countItems(setId));

        } finally {
            start.countDown();
            pool.shutdownNow();
            pool.awaitTermination(30, TimeUnit.SECONDS);
            cleanup(setId);
        }
    }

    @Test
    void invalidPostureItemRollsBackEntireSet() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();

        try {
            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.saveSet(
                    userId, sessionId, setId, payload(false)
                )
            );

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertEquals(0, countSets(setId));
            assertEquals(0, countItems(setId));

        } finally {
            cleanup(setId);
        }
    }

    @Test
    void differentUserCannotReuseExistingSetId() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();

        try {
            service.saveSet(owner, sessionId, setId, payload(true));

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.saveSet(
                    attacker, sessionId, setId, payload(true)
                )
            );

            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
            assertEquals(1, countSets(setId));
            assertEquals(1, countItems(setId));

        } finally {
            cleanup(setId);
        }
    }
}