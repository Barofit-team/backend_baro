
package com.barofit.analytics.service;

import com.barofit.analytics.repository.CalendarEntryRepository;
import com.barofit.analytics.repository.DailyStatisticRepository;
import com.barofit.analytics.repository.WorkoutRecordViewRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

@Service
public class WorkoutCompletionService {

    private static final ZoneId WORKOUT_ZONE =
            ZoneId.of("Asia/Seoul");

    private final WorkoutRecordViewRepository workoutRepository;
    private final DailyStatisticRepository dailyRepository;
    private final CalendarEntryRepository calendarRepository;

    public WorkoutCompletionService(
            WorkoutRecordViewRepository workoutRepository,
            DailyStatisticRepository dailyRepository,
            CalendarEntryRepository calendarRepository
    ) {
        this.workoutRepository = workoutRepository;
        this.dailyRepository = dailyRepository;
        this.calendarRepository = calendarRepository;
    }

    @Transactional
    public void recordCompletedWorkout(
            UUID sessionId,
            UUID userId,
            UUID routineId,
            OffsetDateTime completedAt,
            int durationSeconds,
            int totalReps
    ) {
        Objects.requireNonNull(
                sessionId, "Session ID is required"
        );
        Objects.requireNonNull(
                userId, "User ID is required"
        );
        Objects.requireNonNull(
                completedAt, "Completion time is required"
        );

        if (durationSeconds < 0 || totalReps < 0) {
            throw new IllegalArgumentException(
                    "Workout values must not be negative"
            );
        }

        // PostgreSQL이 세션 중복 여부를 원자적으로 판단
        int insertedRows = workoutRepository.insertIfAbsent(
                sessionId,
                userId,
                routineId,
                completedAt,
                durationSeconds,
                totalReps
        );

        // 이미 저장된 세션이면 통계를 다시 집계하지 않음
        if (insertedRows == 0) {
            return;
        }

        if (insertedRows != 1) {
            throw new IllegalStateException(
                    "Unexpected workout insert result: " + insertedRows
            );
        }

        // 한국 시간 기준 운동 날짜
        LocalDate workoutDate = completedAt
                .atZoneSameInstant(WORKOUT_ZONE)
                .toLocalDate();

        // 일별 통계 원자적 갱신
        dailyRepository.incrementWorkoutStatistics(
                userId,
                workoutDate,
                durationSeconds,
                totalReps
        );

        // 운동 캘린더 원자적 갱신
        calendarRepository.incrementCalendarEntry(
                userId,
                workoutDate,
                durationSeconds
        );
    }
}
