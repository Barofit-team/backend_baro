package com.barofit.report.controller;

import com.barofit.report.service.ReportService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Report Service", description = "Exercise set results and analytics reports")
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
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
    public Map<String, Object> saveSet(
            HttpServletRequest request,
            @PathVariable UUID sessionId,
            @PathVariable UUID setId,
            @RequestBody JsonNode payload) {

        return service.saveSet(
            currentUser(request), sessionId, setId, payload);
    }

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
