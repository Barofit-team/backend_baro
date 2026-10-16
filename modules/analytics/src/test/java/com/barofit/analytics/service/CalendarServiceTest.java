
package com.barofit.analytics.service;

import com.barofit.analytics.entity.CalendarEntry;
import com.barofit.analytics.repository.CalendarEntryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalendarServiceTest {

    private CalendarEntryRepository repository;
    private CalendarService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        repository = mock(CalendarEntryRepository.class);
        service = new CalendarService(repository);
        userId = UUID.randomUUID();
    }

    @Test
    void getMonthlyCalendar_usesCorrectDateRange() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);

        CalendarEntry entry = new CalendarEntry(userId, start);

        when(repository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                userId, start, end
        )).thenReturn(List.of(entry));

        var result = service.getMonthlyCalendar(userId, 2026, 10);

        assertEquals(1, result.size());
        assertEquals(start, result.get(0).workoutDate());
        assertEquals(0, result.get(0).sessionCount());

        verify(repository).findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                userId, start, end
        );
    }

    @Test
    void getMonthlyCalendar_handlesLeapYear() {
        LocalDate start = LocalDate.of(2028, 2, 1);
        LocalDate end = LocalDate.of(2028, 2, 29);

        when(repository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                userId, start, end
        )).thenReturn(List.of());

        var result = service.getMonthlyCalendar(userId, 2028, 2);

        assertTrue(result.isEmpty());

        verify(repository).findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                userId, start, end
        );
    }

    @Test
    void getMonthlyCalendar_rejectsInvalidMonth() {
        assertThrows(
                java.time.DateTimeException.class,
                () -> service.getMonthlyCalendar(userId, 2026, 13)
        );

        verifyNoInteractions(repository);
    }
}
