package com.barofit.workout;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 세션 종료와 같은 트랜잭션에서 쌓인 outbox를 Analytics에 멱등 전달한다. */
@Component
public class AnalyticsOutboxDelivery {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsOutboxDelivery.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final URI baseUri;
    private final String internalToken;

    public AnalyticsOutboxDelivery(JdbcTemplate jdbc, ObjectMapper json,
            @Value("${barofit.analytics.base-url:http://localhost:3007}") String baseUrl,
            @Value("${barofit.analytics.internal-token:}") String internalToken) {
        this.jdbc = jdbc;
        this.json = json;
        this.baseUri = URI.create(baseUrl);
        this.internalToken = internalToken;
    }

    boolean configured() { return !internalToken.isBlank(); }

    @Transactional
    public void deliver(UUID sessionId) {
        List<Completion> rows = jdbc.query("""
            SELECT o.session_id,o.attempts,s.user_id,s.routine_id,s.ended_at,s.active_duration_seconds,
                   (SELECT COALESCE(SUM(r.actual_reps),0)
                    FROM workout.workout_set_result r WHERE r.session_id=s.id) AS total_reps
            FROM workout.analytics_completion_outbox o
            JOIN workout.workout_session s ON s.id=o.session_id
            WHERE o.session_id=? AND o.delivered_at IS NULL AND o.next_attempt_at<=now()
            FOR UPDATE OF o SKIP LOCKED
            """, (rs, row) -> new Completion(
                (UUID) rs.getObject("session_id"), rs.getInt("attempts"),
                (UUID) rs.getObject("user_id"), (UUID) rs.getObject("routine_id"),
                rs.getTimestamp("ended_at"), rs.getInt("active_duration_seconds"),
                rs.getLong("total_reps")), sessionId);
        if (rows.isEmpty()) return;
        Completion completion = rows.getFirst();
        try {
            int totalReps = Math.toIntExact(completion.totalReps());
            String body = json.writeValueAsString(Map.of(
                "userId", completion.userId().toString(),
                "routineId", completion.routineId().toString(),
                "completedAt", completion.endedAt().toInstant().toString(),
                "durationSeconds", completion.durationSeconds(),
                "totalReps", totalReps));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(
                "/internal/workout-completions/" + completion.sessionId()))
                .header("Content-Type", "application/json")
                .header("X-Internal-Token", internalToken)
                .timeout(Duration.ofSeconds(4))
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 200 && status < 300) {
                jdbc.update("UPDATE workout.analytics_completion_outbox SET delivered_at=now() WHERE session_id=?",
                    sessionId);
                return;
            }
            log.warn("Analytics completion delivery failed: sessionId={}, status={}", sessionId, status);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Analytics completion delivery interrupted: sessionId={}", sessionId);
        } catch (Exception ex) {
            log.warn("Analytics completion delivery failed: sessionId={}, cause={}",
                sessionId, ex.getClass().getSimpleName());
        }
        int delaySeconds = Math.min(300, 5 << Math.min(completion.attempts(), 6));
        jdbc.update("""
            UPDATE workout.analytics_completion_outbox
            SET attempts=attempts+1,next_attempt_at=now()+(? * interval '1 second')
            WHERE session_id=?
            """, delaySeconds, sessionId);
    }

    private record Completion(UUID sessionId, int attempts, UUID userId, UUID routineId,
                              Timestamp endedAt, int durationSeconds, long totalReps) { }
}
