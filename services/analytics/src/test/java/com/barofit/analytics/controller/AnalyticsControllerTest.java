
package com.barofit.analytics.controller;

import com.barofit.analytics.service.CalendarService;
import com.barofit.analytics.service.StatisticsService;
import com.barofit.analytics.service.WorkoutRecordService;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalyticsControllerTest {

    private WorkoutRecordController workoutController;
    private StatisticsController statisticsController;
    private CalendarController calendarController;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        workoutController = new WorkoutRecordController(
                mock(WorkoutRecordService.class)
        );

        statisticsController = new StatisticsController(
                mock(StatisticsService.class)
        );

        calendarController = new CalendarController(
                mock(CalendarService.class)
        );

        request = mock(HttpServletRequest.class);

        when(request.getUserPrincipal()).thenReturn(null);
    }

    @Test
    void workoutListRequiresAuthentication() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> workoutController.getWorkoutRecords(
                        request, 0, 20
                )
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );
    }

    @Test
    void workoutDetailRequiresAuthentication() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> workoutController.getWorkoutRecord(
                        request, UUID.randomUUID()
                )
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );
    }

    @Test
    void dailyStatisticsRequireAuthentication() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> statisticsController.getDaily(
                        request,
                        java.time.LocalDate.of(2026, 10, 1),
                        java.time.LocalDate.of(2026, 10, 19)
                )
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );
    }

    @Test
    void summaryRequiresAuthentication() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> statisticsController.getSummary(
                        request,
                        java.time.LocalDate.of(2026, 10, 1),
                        java.time.LocalDate.of(2026, 10, 19)
                )
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );
    }

    @Test
    void weeklyStatisticsRequireAuthentication() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> statisticsController.getWeekly(
                        request,
                        java.time.LocalDate.of(2026, 10, 19)
                )
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );
    }

    @Test
    void monthlyStatisticsRequireAuthentication() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> statisticsController.getMonthly(
                        request, 2026, 10
                )
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );
    }

    @Test
    void calendarRequiresAuthentication() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> calendarController.getMonthlyCalendar(
                        request, 2026, 10
                )
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );
    }
}
