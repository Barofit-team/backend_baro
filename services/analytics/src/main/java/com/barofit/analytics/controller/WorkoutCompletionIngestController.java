package com.barofit.analytics.controller;

import com.barofit.analytics.service.WorkoutCompletionService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Gateway에 노출하지 않는 Workout → Analytics 집계 수신 API. */
@RestController
@RequestMapping("/internal/workout-completions")
public class WorkoutCompletionIngestController {
    private final WorkoutCompletionService completions;
    private final String internalToken;

    public WorkoutCompletionIngestController(WorkoutCompletionService completions,
            @Value("${barofit.analytics.internal-token:}") String internalToken) {
        this.completions = completions;
        this.internalToken = internalToken;
    }

    public record Completion(UUID userId, UUID routineId, OffsetDateTime completedAt,
                             Integer durationSeconds, Integer totalReps) { }

    @PutMapping("/{sessionId}")
    public void ingest(@PathVariable UUID sessionId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody Completion completion) {
        if (internalToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Internal ingestion is not configured");
        }
        if (token == null || !MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal token");
        }
        if (completion == null || completion.userId() == null || completion.completedAt() == null
            || completion.durationSeconds() == null || completion.totalReps() == null
            || completion.durationSeconds() < 0 || completion.totalReps() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid workout completion");
        }
        completions.recordCompletedWorkout(sessionId, completion.userId(), completion.routineId(),
            completion.completedAt(), completion.durationSeconds(), completion.totalReps());
    }
}
