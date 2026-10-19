
package com.barofit.analytics.service;

import com.barofit.analytics.repository.CalendarEntryRepository;
import com.barofit.analytics.repository.DailyStatisticRepository;
import com.barofit.analytics.repository.WorkoutRecordViewRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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

        when(workoutRepository.insertIfAbsent(
                sessionId, userId, routineId,
                completedAt, 1800, 100
        )).thenReturn(1);

        service.recordCompletedWorkout(
                sessionId, userId, routineId,
                completedAt, 1800, 100
        );

        verify(workoutRepository).insertIfAbsent(
                sessionId, userId, routineId,
                completedAt, 1800, 100
        );

        verify(dailyRepository).incrementWorkoutStatistics(
                userId, date, 1800, 100
        );

        verify(calendarRepository).incrementCalendarEntry(
                userId, date, 1800
        );
    }

    @Test
    void ignoresDuplicateSession() {
        when(workoutRepository.insertIfAbsent(
                sessionId, userId, routineId,
                completedAt, 1800, 100
        )).thenReturn(0);

        service.recordCompletedWorkout(
                sessionId, userId, routineId,
                completedAt, 1800, 100
        );

        verifyNoInteractions(dailyRepository, calendarRepository);
    }

    @Test
    void addsWorkoutToExistingStatistics() {
        LocalDate date = LocalDate.of(2026, 10, 16);

        when(workoutRepository.insertIfAbsent(
                sessionId, userId, routineId,
                completedAt, 1800, 100
        )).thenReturn(1);

        service.recordCompletedWorkout(
                sessionId, userId, routineId,
                completedAt, 1800, 100
        );

        verify(dailyRepository).incrementWorkoutStatistics(
                userId, date, 1800, 100
        );

        verify(calendarRepository).incrementCalendarEntry(
                userId, date, 1800
        );
    }

    @Test
    void rejectsNegativeWorkoutValues() {
        assertThrows(IllegalArgumentException.class,
                () -> service.recordCompletedWorkout(
                        sessionId, userId, routineId,
                        completedAt, -1, 100
                ));

        assertThrows(IllegalArgumentException.class,
                () -> service.recordCompletedWorkout(
                        sessionId, userId, routineId,
                        completedAt, 1800, -1
                ));

        verifyNoInteractions(
                workoutRepository, dailyRepository, calendarRepository
        );
    }

    @Test
    void usesSeoulDateForWorkout() {
        OffsetDateTime utcTime = OffsetDateTime.parse(
                "2026-10-16T16:30:00Z"
        );
        LocalDate seoulDate = LocalDate.of(2026, 10, 17);

        when(workoutRepository.insertIfAbsent(
                sessionId, userId, routineId,
                utcTime, 600, 20
        )).thenReturn(1);

        service.recordCompletedWorkout(
                sessionId, userId, routineId,
                utcTime, 600, 20
        );

        verify(dailyRepository).incrementWorkoutStatistics(
                userId, seoulDate, 600, 20
        );

        verify(calendarRepository).incrementCalendarEntry(
                userId, seoulDate, 600
        );
    }

    @Test
    void rejectsUnexpectedInsertResult() {
        when(workoutRepository.insertIfAbsent(
                sessionId, userId, routineId,
                completedAt, 1800, 100
        )).thenReturn(2);

        assertThrows(IllegalStateException.class,
                () -> service.recordCompletedWorkout(
                        sessionId, userId, routineId,
                        completedAt, 1800, 100
                ));

        verifyNoInteractions(dailyRepository, calendarRepository);
    }
}
