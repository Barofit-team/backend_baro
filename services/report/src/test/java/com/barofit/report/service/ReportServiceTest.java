package com.barofit.report.service;

import com.barofit.report.repository.ReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

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
}