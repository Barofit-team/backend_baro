
package com.barofit.analytics.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class CalendarEntryId implements Serializable {

    private UUID userId;
    private LocalDate workoutDate;

    public CalendarEntryId() {
    }

    public CalendarEntryId(UUID userId, LocalDate workoutDate) {
        this.userId = userId;
        this.workoutDate = workoutDate;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getWorkoutDate() {
        return workoutDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CalendarEntryId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId)
                && Objects.equals(workoutDate, that.workoutDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, workoutDate);
    }
}
