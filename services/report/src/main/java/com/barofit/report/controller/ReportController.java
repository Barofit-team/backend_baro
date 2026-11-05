package com.barofit.report.controller;

import com.barofit.report.service.ReportService;
import com.barofit.report.service.WorkoutSessionOwnershipClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Report Service", description = "Exercise set results and analytics reports")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService service;
    private final WorkoutSessionOwnershipClient ownership;
    private final ObjectMapper mapper;
    @org.springframework.beans.factory.annotation.Autowired
    private com.barofit.report.service.ReportSourceClient sources;

    public ReportController(ReportService service, WorkoutSessionOwnershipClient ownership, ObjectMapper mapper) {
        this.service = service;
        this.ownership = ownership;
        this.mapper = mapper;
    }

    private UUID currentUser(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();

        if (principal == null) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Invalid authenticated user");
        }
    }

    @PostMapping("/sessions/{sessionId}/sets/{setId}")
    @ResponseStatus(HttpStatus.CREATED)
    @io.swagger.v3.oas.annotations.Operation(summary = "온디바이스 세트 결과 저장",
        description = "AI의 set.completed 2.0 원문을 보냅니다. 점수 집계를 위해 X-Routine-Set-Id에 운동 계획의 세트 ID를 전달합니다. 오프라인 수행 시각은 X-Workout-Performed-At에 ISO 8601로 전달합니다.")
    public Map<String, Object> saveSet(
            HttpServletRequest request,
            @PathVariable UUID sessionId,
            @PathVariable UUID setId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                content = @io.swagger.v3.oas.annotations.media.Content(
                    mediaType = "application/json",
                    schema = @io.swagger.v3.oas.annotations.media.Schema(type = "object"),
                    examples = @io.swagger.v3.oas.annotations.media.ExampleObject(value = """
                        {"schemaVersion":"2.0","type":"set.completed","exercise":"pushup",
                         "engineVersion":"pose-2.0","timestampMs":10000,
                         "summary":{"schemaVersion":"2.0","exercise":"pushup","engineVersion":"pose-2.0",
                         "count":10,"holdSeconds":null,"observedSeconds":null,"gapSeconds":null,
                         "items":{"hip_alignment":{"aggregationBasis":"repetition","unit":"repetitions",
                         "compliant":8,"warning":2,"unknown":0,"notApplicable":0,"evaluated":10,"compliancePct":80}},"details":{}}}
                        """)))
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Workout-Performed-At", required = false) String performedAt,
            @RequestHeader(value = "X-Routine-Set-Id", required = false) String routineSetHeader) {
        UUID userId = currentUser(request);
        JsonNode payload;
        try {
            payload = mapper.readTree(rawPayload);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report JSON", ex);
        }
        ownership.requireOwned(sessionId, request.getHeader("Authorization"));
        if (routineSetHeader == null || routineSetHeader.isBlank())
            return service.saveSet(userId, sessionId, setId, payload, performedAt);
        UUID routineSetId;
        try { routineSetId = UUID.fromString(routineSetHeader); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid routine set ID"); }
        JsonNode session = sources.workout("/api/v1/workout-sessions/" + sessionId, request.getHeader("Authorization"));
        boolean matches = false;
        for (JsonNode exercise : session.path("routineSnapshot").path("exercises")) {
            for (JsonNode set : exercise.path("sets")) {
                if (routineSetId.toString().equals(set.path("id").asText()) &&
                    normalize(exercise.path("exerciseId").asText()).equals(normalize(payload.path("exercise").asText()))) matches = true;
            }
        }
        if (!matches) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Routine set or exercise does not match workout");
        return service.saveSet(userId, sessionId, setId, payload, performedAt, routineSetId);
    }

    private static String normalize(String code) { return code.replace("-", "").replace("_", "").toLowerCase(java.util.Locale.ROOT); }

    @GetMapping("/sessions/{sessionId}/sets")
    public List<Map<String, Object>> getSessionSets(
            HttpServletRequest request,
            @PathVariable UUID sessionId) {

        return service.getSessionSets(
            currentUser(request), sessionId);
    }

    @GetMapping("/sessions/{sessionId}/summary")
    public Map<String, Object> getSessionSummary(
            HttpServletRequest request,
            @PathVariable UUID sessionId) {

        return service.getSessionSummary(
            currentUser(request), sessionId);
    }
    @GetMapping("/daily")
    public Map<String, Object> daily(
            HttpServletRequest request,
            @RequestParam
            @org.springframework.format.annotation.DateTimeFormat(
                iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE
            )
            java.time.LocalDate date) {

        return service.getDailySummary(currentUser(request), date);
    }

    @GetMapping("/weekly")
    public Map<String, Object> weekly(
            HttpServletRequest request,
            @RequestParam
            @org.springframework.format.annotation.DateTimeFormat(
                iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE
            )
            java.time.LocalDate startDate) {

        return service.getWeeklySummary(
            currentUser(request), startDate);
    }
}
