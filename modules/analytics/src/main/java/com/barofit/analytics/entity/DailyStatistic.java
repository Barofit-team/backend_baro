
package com.barofit.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_statistics")
@IdClass(DailyStatisticId.class)
public class DailyStatistic {

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

    @Column(name = "total_reps", nullable = false)
    private Integer totalReps = 0;

    protected DailyStatistic() {
    }

    public DailyStatistic(UUID userId, LocalDate workoutDate) {
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

    public Integer getTotalReps() {
        return totalReps;
    }
}
