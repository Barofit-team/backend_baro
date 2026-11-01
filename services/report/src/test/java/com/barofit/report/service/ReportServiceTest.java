package com.barofit.report.service;

import com.barofit.report.repository.ReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import static org.mockito.ArgumentMatchers.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportServiceTest {

    private ReportRepository repository;
    private ReportService service;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        repository = mock(ReportRepository.class);
        mapper = new ObjectMapper();
        service = new ReportService(repository, mapper);
    }

    @Test
    void rejectsUnsupportedSchemaVersion() throws Exception {
        var payload = mapper.readTree("""
            {
              "schemaVersion": "1.0",
              "type": "set.completed",
              "exercise": "pushup",
              "engineVersion": "test",
              "summary": {}
            }
            """);

        assertThrows(
            ResponseStatusException.class,
            () -> service.saveSet(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                payload
            )
        );

        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMissingSummary() throws Exception {
        var payload = mapper.readTree("""
            {
              "schemaVersion": "2.0",
              "type": "set.completed",
              "exercise": "pushup",
              "engineVersion": "test"
            }
            """);

        assertThrows(
            ResponseStatusException.class,
            () -> service.saveSet(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                payload
            )
        );

        verifyNoInteractions(repository);
    }

    @Test
    void acceptsNewExerciseAndPreservesOfflinePerformedTime() throws Exception {
        UUID user = UUID.randomUUID(), session = UUID.randomUUID(), set = UUID.randomUUID();
        when(repository.findSet(set)).thenReturn(null);
        var payload = mapper.readTree("""
            {"schemaVersion":"2.0","type":"set.completed","exercise":"squat","engineVersion":"test",
             "summary":{"schemaVersion":"2.0","exercise":"squat","engineVersion":"test",
                        "count":10,"items":{}}}
            """);
        service.saveSet(user, session, set, payload, "2026-10-25T15:10:00Z");
        verify(repository).insertSet(eq(user), eq(session), eq(set), eq("squat"), eq("test"),
            isNull(), eq(10), isNull(), isNull(), isNull(),
            eq(Instant.parse("2026-10-25T15:10:00Z")), anyString(), isNull());
    }

    @Test
    void dailyUsesSeoulMidnightForOfflineRecords() {
        UUID user = UUID.randomUUID();
        service.getDailySummary(user, LocalDate.of(2026, 10, 26));
        verify(repository).findPeriodSummary(user, Instant.parse("2026-10-25T15:00:00Z"),
            Instant.parse("2026-10-26T15:00:00Z"));
    }
}
