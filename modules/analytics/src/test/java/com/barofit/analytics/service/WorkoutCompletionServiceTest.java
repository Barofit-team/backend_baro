
package com.barofit.analytics.service;

import com.barofit.analytics.entity.WorkoutRecordView;
import com.barofit.analytics.repository.CalendarEntryRepository;
import com.barofit.analytics.repository.DailyStatisticRepository;
import com.barofit.analytics.repository.WorkoutRecordViewRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkoutCompletionServiceTest {

    private WorkoutRecordViewRepository workoutRepository;
    private DailyStatisticRepository dailyRepository;
    private CalendarEntryRepository calendarRepository;

    private WorkoutCompletionService service;

    private UUID sessionId;
    private UUID userId;
    private UUID routineId;
    private OffsetDateTime completedAt;

    @BeforeEach
    void setUp() {
        workoutRepository = mock(WorkoutRecordViewRepository.class);
        dailyRepository = mock(DailyStatisticRepository.class);
        calendarRepository = mock(CalendarEntryRepository.class);

        service = new WorkoutCompletionService(
                workoutRepository,
                dailyRepository,
                calendarRepository
        );

        sessionId = UUID.randomUUID();
        userId = UUID.randomUUID();
        routineId = UUID.randomUUID();

        completedAt = OffsetDateTime.parse(
                "2026-10-16T18:30:00+09:00"
        );
    }

    @Test
    void savesCompletedWorkoutAndUpdatesStatistics() {
        LocalDate date = LocalDate.of(2026, 10, 16);

        service.recordCompletedWorkout(
                sessionId,
                userId,
                routineId,
                completedAt,
                1800,
                100
        );

        verify(workoutRepository)
                .saveAndFlush(any(WorkoutRecordView.class));

        verify(dailyRepository).incrementWorkoutStatistics(
                userId,
                date,
                1800,
                100
        );

        verify(calendarRepository).incrementCalendarEntry(
                userId,
                date,
                1800
        );
    }

    @Test
    void ignoresDuplicateSession() {
        when(workoutRepository.existsById(sessionId))
                .thenReturn(true);

        service.recordCompletedWorkout(
                sessionId,
                userId,
                routineId,
                completedAt,
                1800,
                100
        );

        verify(workoutRepository, never())
                .saveAndFlush(any());

        verifyNoInteractions(
                dailyRepository,
                calendarRepository
        );
    }

    @Test
    void addsWorkoutToExistingStatistics() {
        LocalDate date = LocalDate.of(2026, 10, 16);

        service.recordCompletedWorkout(
                sessionId,
                userId,
                routineId,
                completedAt,
                1800,
                100
        );

        // 기존 값의 누적은 PostgreSQL ON CONFLICT가 처리한다.
        // 단위 테스트에서는 원자적 증가 메서드 호출을 검증한다.
        verify(dailyRepository).incrementWorkoutStatistics(
                userId,
                date,
                1800,
                100
        );

        verify(calendarRepository).incrementCalendarEntry(
                userId,
                date,
                1800
        );
    }

    @Test
    void rejectsNegativeWorkoutValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.recordCompletedWorkout(
                        sessionId,
                        userId,
                        routineId,
                        completedAt,
                        -1,
                        100
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.recordCompletedWorkout(
                        sessionId,
                        userId,
                        routineId,
                        completedAt,
                        1800,
                        -1
                )
        );

        verifyNoInteractions(
                workoutRepository,
                dailyRepository,
                calendarRepository
        );
    }

    @Test
    void usesSeoulDateForWorkout() {
        OffsetDateTime utcTime = OffsetDateTime.parse(
                "2026-10-16T16:30:00Z"
        );

        LocalDate seoulDate = LocalDate.of(2026, 10, 17);

        service.recordCompletedWorkout(
                sessionId,
                userId,
                routineId,
                utcTime,
                600,
                20
        );

        verify(dailyRepository).incrementWorkoutStatistics(
                userId,
                seoulDate,
                600,
                20
        );

        verify(calendarRepository).incrementCalendarEntry(
                userId,
                seoulDate,
                600
        );
    }
}
