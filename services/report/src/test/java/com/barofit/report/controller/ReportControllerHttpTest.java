package com.barofit.report.controller;

import com.barofit.report.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReportControllerHttpTest {

    private final ReportService service = mock(ReportService.class);

    private final MockMvc mvc = MockMvcBuilders
        .standaloneSetup(new ReportController(service))
        .setMessageConverters(
            new MappingJackson2HttpMessageConverter(new ObjectMapper())
        )
        .build();

    @Test
    void rejectsUnauthenticatedSessionSummary() throws Exception {

        UUID sessionId = UUID.randomUUID();

        mvc.perform(get("/api/v1/reports/sessions/" + sessionId + "/summary"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void authenticatedUserCanReadOwnSummary() throws Exception {

        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        when(service.getSessionSummary(userId, sessionId))
            .thenReturn(Map.of(
                "sessionId", sessionId.toString(),
                "setCount", 2,
                "totalReps", 20,
                "postureItems", List.of()
            ));

        Principal principal = () -> userId.toString();

        mvc.perform(
                get("/api/v1/reports/sessions/" + sessionId + "/summary")
                    .principal(principal)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.setCount").value(2))
            .andExpect(jsonPath("$.totalReps").value(20));

        verify(service).getSessionSummary(userId, sessionId);
    }

    @Test
    void authenticatedUserCanReadSessionSets() throws Exception {

        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        when(service.getSessionSets(userId, sessionId))
            .thenReturn(List.of());

        Principal principal = () -> userId.toString();

        mvc.perform(
                get("/api/v1/reports/sessions/" + sessionId + "/sets")
                    .principal(principal)
            )
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));

        verify(service).getSessionSets(userId, sessionId);
    }

    @Test
    void authenticatedUserCanSaveSet() throws Exception {

        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();

        Principal principal = () -> userId.toString();

        String payload = """
            {
              "schemaVersion": "2.0",
              "type": "set.completed",
              "exercise": "pushup",
              "engineVersion": "http-test",
              "summary": {
                "schemaVersion": "2.0",
                "exercise": "pushup",
                "engineVersion": "http-test",
                "count": 10,
                "items": {}
              }
            }
            """;

        when(service.saveSet(
                eq(userId),
                eq(sessionId),
                eq(setId),
                any(com.fasterxml.jackson.databind.JsonNode.class)
            ))
            .thenReturn(Map.of(
                "setId", setId,
                "sessionId", sessionId,
                "status", "saved"
            ));

        mvc.perform(
                post("/api/v1/reports/sessions/" + sessionId + "/sets/" + setId)
                    .principal(principal)
                    .contentType("application/json")
                    .content(payload)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("saved"));

        verify(service).saveSet(
            eq(userId),
            eq(sessionId),
            eq(setId),
            any(com.fasterxml.jackson.databind.JsonNode.class)
        );
    }

    @Test
    void rejectsUnauthenticatedSetSubmission() throws Exception {

        UUID sessionId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();

        mvc.perform(
                post("/api/v1/reports/sessions/" + sessionId + "/sets/" + setId)
                    .contentType("application/json")
                    .content("{}")
            )
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidSetPayload() throws Exception {

        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();

        Principal principal = () -> userId.toString();

        when(service.saveSet(
                eq(userId),
                eq(sessionId),
                eq(setId),
                any(com.fasterxml.jackson.databind.JsonNode.class)
            ))
            .thenThrow(new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unsupported Pose contract"
            ));

        mvc.perform(
                post("/api/v1/reports/sessions/" + sessionId + "/sets/" + setId)
                    .principal(principal)
                    .contentType("application/json")
                    .content("""
                        {
                          "schemaVersion": "1.0",
                          "type": "set.completed",
                          "exercise": "pushup",
                          "engineVersion": "http-test",
                          "summary": {}
                        }
                        """)
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsConflictingSetSubmission() throws Exception {

        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();

        Principal principal = () -> userId.toString();

        when(service.saveSet(
                eq(userId),
                eq(sessionId),
                eq(setId),
                any(com.fasterxml.jackson.databind.JsonNode.class)
            ))
            .thenThrow(new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Set ID reused with different payload"
            ));

        mvc.perform(
                post("/api/v1/reports/sessions/" + sessionId + "/sets/" + setId)
                    .principal(principal)
                    .contentType("application/json")
                    .content("""
                        {
                          "schemaVersion": "2.0",
                          "type": "set.completed",
                          "exercise": "pushup",
                          "engineVersion": "http-test",
                          "summary": {
                            "schemaVersion": "2.0",
                            "exercise": "pushup",
                            "engineVersion": "http-test",
                            "count": 10,
                            "items": {}
                          }
                        }
                        """)
            )
            .andExpect(status().isConflict());
    }
}