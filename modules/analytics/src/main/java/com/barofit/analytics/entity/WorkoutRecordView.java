
package com.barofit.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "workout_record_views")
public class WorkoutRecordView {

    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "routine_id")
    private UUID routineId;

    @Column(name = "completed_at", nullable = false)
    private OffsetDateTime completedAt;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds;

    @Column(name = "total_reps", nullable = false)
    private Integer totalReps;

    protected WorkoutRecordView() {
    }

    public WorkoutRecordView(
            UUID sessionId,
            UUID userId,
            UUID routineId,
            OffsetDateTime completedAt,
            Integer durationSeconds,
            Integer totalReps
    ) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.routineId = routineId;
        this.completedAt = completedAt;
        this.durationSeconds = durationSeconds;
        this.totalReps = totalReps;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getRoutineId() {
        return routineId;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public Integer getTotalReps() {
        return totalReps;
    }
}
