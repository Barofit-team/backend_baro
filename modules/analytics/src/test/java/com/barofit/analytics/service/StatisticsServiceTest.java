
package com.barofit.analytics.service;

import com.barofit.analytics.entity.DailyStatistic;
import com.barofit.analytics.repository.DailyStatisticRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StatisticsServiceTest {

    private DailyStatisticRepository repository;
    private StatisticsService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        repository = mock(DailyStatisticRepository.class);
        service = new StatisticsService(repository);
        userId = UUID.randomUUID();
    }

    @Test
    void getDailyStatistics_returnsRecords() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 7);

        DailyStatistic statistic = new DailyStatistic(userId, start);

        when(repository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                userId, start, end
        )).thenReturn(List.of(statistic));

        var result = service.getDailyStatistics(userId, start, end);

        assertEquals(1, result.size());
        assertEquals(start, result.get(0).workoutDate());
        assertEquals(0, result.get(0).sessionCount());

        verify(repository).findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                userId, start, end
        );
    }

    @Test
    void getDailyStatistics_rejectsInvalidDateRange() {
        LocalDate start = LocalDate.of(2026, 10, 10);
        LocalDate end = LocalDate.of(2026, 10, 1);

        assertThrows(IllegalArgumentException.class,
                () -> service.getDailyStatistics(userId, start, end));

        verifyNoInteractions(repository);
    }

    @Test
    void getDailyStatistics_rejectsNullDates() {
        LocalDate date = LocalDate.of(2026, 10, 15);

        assertThrows(IllegalArgumentException.class,
                () -> service.getDailyStatistics(userId, null, date));

        assertThrows(IllegalArgumentException.class,
                () -> service.getDailyStatistics(userId, date, null));

        verifyNoInteractions(repository);
    }

    @Test
    void getStatisticsSummary_sumsMultipleDays() {
        LocalDate start = LocalDate.of(2026, 10, 12);
        LocalDate end = LocalDate.of(2026, 10, 14);

        DailyStatistic first = mock(DailyStatistic.class);
        when(first.getWorkoutDate()).thenReturn(start);
        when(first.getSessionCount()).thenReturn(1);
        when(first.getTotalDurationSeconds()).thenReturn(1800);
        when(first.getTotalReps()).thenReturn(100);

        DailyStatistic second = mock(DailyStatistic.class);
        when(second.getWorkoutDate()).thenReturn(start.plusDays(1));
        when(second.getSessionCount()).thenReturn(2);
        when(second.getTotalDurationSeconds()).thenReturn(3000);
        when(second.getTotalReps()).thenReturn(150);

        DailyStatistic third = mock(DailyStatistic.class);
        when(third.getWorkoutDate()).thenReturn(end);
        when(third.getSessionCount()).thenReturn(1);
        when(third.getTotalDurationSeconds()).thenReturn(2400);
        when(third.getTotalReps()).thenReturn(120);

        when(repository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                userId, start, end
        )).thenReturn(List.of(first, second, third));

        var result = service.getStatisticsSummary(userId, start, end);

        assertEquals(4, result.sessionCount());
        assertEquals(7200, result.totalDurationSeconds());
        assertEquals(370, result.totalReps());

        verify(repository).findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                userId, start, end
        );
    }

    @Test
    void getStatisticsSummary_returnsZeroWhenEmpty() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 7);

        when(repository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                userId, start, end
        )).thenReturn(List.of());

        var result = service.getStatisticsSummary(userId, start, end);

        assertEquals(0, result.sessionCount());
        assertEquals(0, result.totalDurationSeconds());
        assertEquals(0, result.totalReps());
    }

    @Test
    void getStatisticsSummary_rejectsInvalidDateRange() {
        LocalDate start = LocalDate.of(2026, 10, 10);
        LocalDate end = LocalDate.of(2026, 10, 1);

        assertThrows(IllegalArgumentException.class,
                () -> service.getStatisticsSummary(userId, start, end));

        verifyNoInteractions(repository);
    }

    @Test
    void getWeeklyStatistics_usesMondayToSunday() {
        LocalDate monday = LocalDate.of(2026, 10, 12);
        LocalDate sunday = LocalDate.of(2026, 10, 18);

        when(repository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                userId, monday, sunday
        )).thenReturn(List.of());

        var result = service.getWeeklyStatistics(
                userId, LocalDate.of(2026, 10, 16)
        );

        assertEquals(0, result.sessionCount());

        verify(repository).findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                userId, monday, sunday
        );
    }

    @Test
    void getWeeklyStatistics_handlesMonthBoundary() {
        LocalDate monday = LocalDate.of(2026, 9, 28);
        LocalDate sunday = LocalDate.of(2026, 10, 4);

        when(repository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                userId, monday, sunday
        )).thenReturn(List.of());

        service.getWeeklyStatistics(
                userId, LocalDate.of(2026, 10, 1)
        );

        verify(repository).findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                userId, monday, sunday
        );
    }

    @Test
    void getWeeklyStatistics_rejectsNullDate() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getWeeklyStatistics(userId, null));

        verifyNoInteractions(repository);
    }

    @Test
    void getMonthlyStatistics_usesFirstAndLastDay() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);

        when(repository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                userId, start, end
        )).thenReturn(List.of());

        service.getMonthlyStatistics(userId, 2026, 10);

        verify(repository).findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                userId, start, end
        );
    }

    @Test
    void getMonthlyStatistics_handlesLeapYear() {
        LocalDate start = LocalDate.of(2028, 2, 1);
        LocalDate end = LocalDate.of(2028, 2, 29);

        when(repository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                userId, start, end
        )).thenReturn(List.of());

        service.getMonthlyStatistics(userId, 2028, 2);

        verify(repository).findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                userId, start, end
        );
    }

    @Test
    void getMonthlyStatistics_rejectsInvalidMonth() {
        assertThrows(DateTimeException.class,
                () -> service.getMonthlyStatistics(userId, 2026, 13));

        verifyNoInteractions(repository);
    }
}
