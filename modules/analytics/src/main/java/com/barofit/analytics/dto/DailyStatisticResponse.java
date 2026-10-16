
package com.barofit.analytics.dto;

import com.barofit.analytics.entity.DailyStatistic;

import java.time.LocalDate;

public record DailyStatisticResponse(
        LocalDate workoutDate,
        Integer sessionCount,
        Integer totalDurationSeconds,
        Integer totalReps
) {

    public static DailyStatisticResponse from(DailyStatistic entity) {
        return new DailyStatisticResponse(
                entity.getWorkoutDate(),
                entity.getSessionCount(),
                entity.getTotalDurationSeconds(),
                entity.getTotalReps()
        );
    }
}
