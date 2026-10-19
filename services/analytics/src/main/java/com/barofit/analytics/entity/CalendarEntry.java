
package com.barofit.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "calendar_entries")
@IdClass(CalendarEntryId.class)
public class CalendarEntry {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "workout_date", nullable = false)
    private LocalDate workoutDate;

    @Column(name = "session_count", nullable = false)
    private Integer sessionCount = 0;

    @Column(name = "total_duration_seconds", nullable = false)
    private Integer totalDurationSeconds = 0;

    protected CalendarEntry() {
    }

    public CalendarEntry(UUID userId, LocalDate workoutDate) {
        this.userId = userId;
        this.workoutDate = workoutDate;
    }

    public UUID getUserId() {
        return userId;
    }

    public LocalDate getWorkoutDate() {
        return workoutDate;
    }

    public Integer getSessionCount() {
        return sessionCount;
    }

    public Integer getTotalDurationSeconds() {
        return totalDurationSeconds;
    }

    public void addWorkout(int durationSeconds) {
        if (durationSeconds < 0) {
            throw new IllegalArgumentException(
                    "Workout duration must not be negative"
            );
        }

        this.sessionCount = Math.addExact(
                this.sessionCount, 1
        );

        this.totalDurationSeconds = Math.addExact(
                this.totalDurationSeconds, durationSeconds
        );
    }
}
