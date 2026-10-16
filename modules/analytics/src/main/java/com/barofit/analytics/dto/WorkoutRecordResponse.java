
package com.barofit.analytics.dto;

import com.barofit.analytics.entity.WorkoutRecordView;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkoutRecordResponse(
        UUID sessionId,
        UUID routineId,
        OffsetDateTime completedAt,
        Integer durationSeconds,
        Integer totalReps
) {

    public static WorkoutRecordResponse from(WorkoutRecordView entity) {
        return new WorkoutRecordResponse(
                entity.getSessionId(),
                entity.getRoutineId(),
                entity.getCompletedAt(),
                entity.getDurationSeconds(),
                entity.getTotalReps()
        );
    }
}
