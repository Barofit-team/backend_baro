
package com.barofit.analytics.controller;

import com.barofit.analytics.dto.WorkoutRecordResponse;
import com.barofit.analytics.service.WorkoutRecordService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workout-records")
public class WorkoutRecordController {

    private final WorkoutRecordService service;

    public WorkoutRecordController(WorkoutRecordService service) {
        this.service = service;
    }

    private UUID currentUserId(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authenticated user"
            );
        }
    }

    @GetMapping
    public Page<WorkoutRecordResponse> getWorkoutRecords(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID userId = currentUserId(request.getUserPrincipal());

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid pagination"
            );
        }

        return service.getWorkoutRecords(
                userId,
                PageRequest.of(page, size)
        );
    }

    @GetMapping("/{sessionId}")
    public WorkoutRecordResponse getWorkoutRecord(
            HttpServletRequest request,
            @PathVariable UUID sessionId
    ) {
        UUID userId = currentUserId(request.getUserPrincipal());

        return service.getWorkoutRecord(userId, sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Workout record not found"
                ));
    }
}
