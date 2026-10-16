
package com.barofit.analytics.service;

import com.barofit.analytics.entity.DailyStatistic;
import com.barofit.analytics.repository.DailyStatisticRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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
}
