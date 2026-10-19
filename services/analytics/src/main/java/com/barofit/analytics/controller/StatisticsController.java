
package com.barofit.analytics.controller;

import com.barofit.analytics.dto.DailyStatisticResponse;
import com.barofit.analytics.dto.StatisticsSummaryResponse;
import com.barofit.analytics.service.StatisticsService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import jakarta.servlet.http.HttpServletRequest;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/statistics")
public class StatisticsController {

    private final StatisticsService service;

    public StatisticsController(StatisticsService service) {
        this.service = service;
    }

    private UUID currentUserId(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authenticated user"
            );
        }
    }

    private void validateDateRange(
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (startDate == null
                || endDate == null
                || startDate.isAfter(endDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid date range"
            );
        }
    }

    // 일별 통계
    @GetMapping("/daily")
    public List<DailyStatisticResponse> getDaily(
            HttpServletRequest request,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate
    ) {
        UUID userId = currentUserId(request.getUserPrincipal());
        validateDateRange(startDate, endDate);

        return service.getDailyStatistics(
                userId,
                startDate,
                endDate
        );
    }

    // 기간별 통계 합계
    @GetMapping("/summary")
    public StatisticsSummaryResponse getSummary(
            HttpServletRequest request,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate
    ) {
        UUID userId = currentUserId(request.getUserPrincipal());
        validateDateRange(startDate, endDate);

        return service.getStatisticsSummary(
                userId,
                startDate,
                endDate
        );
    }

    // 주별 통계 (월요일부터 일요일)
    @GetMapping("/weekly")
    public StatisticsSummaryResponse getWeekly(
            HttpServletRequest request,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        return service.getWeeklyStatistics(
                currentUserId(request.getUserPrincipal()),
                date
        );
    }

    // 월별 통계
    @GetMapping("/monthly")
    public StatisticsSummaryResponse getMonthly(
            HttpServletRequest request,
            @RequestParam int year,
            @RequestParam int month
    ) {
        UUID userId = currentUserId(request.getUserPrincipal());

        try {
            return service.getMonthlyStatistics(
                    userId,
                    year,
                    month
            );
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid year or month"
            );
        }
    }
}
