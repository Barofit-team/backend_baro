
package com.barofit.analytics.dto;

import com.barofit.analytics.entity.CalendarEntry;

import java.time.LocalDate;

public record CalendarEntryResponse(
        LocalDate workoutDate,
        Integer sessionCount,
        Integer totalDurationSeconds
) {

    public static CalendarEntryResponse from(CalendarEntry entity) {
        return new CalendarEntryResponse(
                entity.getWorkoutDate(),
                entity.getSessionCount(),
                entity.getTotalDurationSeconds()
        );
    }
}
