
package com.barofit.analytics.service;

import com.barofit.analytics.entity.WorkoutRecordView;
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
        // 1. 필수 입력값 검증
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

        // 2. 이미 처리된 운동 세션이면 중복 집계하지 않음
        if (workoutRepository.existsById(sessionId)) {
            return;
        }

        // 3. 한국 시간 기준 운동 날짜 계산
        LocalDate workoutDate = completedAt
                .atZoneSameInstant(WORKOUT_ZONE)
                .toLocalDate();

        // 4. 운동 완료 기록 저장
        WorkoutRecordView workout = new WorkoutRecordView(
                sessionId,
                userId,
                routineId,
                completedAt,
                durationSeconds,
                totalReps
        );

        workoutRepository.saveAndFlush(workout);

        // 5. 일별 통계 원자적 증가
        dailyRepository.incrementWorkoutStatistics(
                userId,
                workoutDate,
                durationSeconds,
                totalReps
        );

        // 6. 캘린더 통계 원자적 증가
        calendarRepository.incrementCalendarEntry(
                userId,
                workoutDate,
                durationSeconds
        );
    }
}
