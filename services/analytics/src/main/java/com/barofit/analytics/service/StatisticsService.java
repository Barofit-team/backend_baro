
package com.barofit.analytics.service;

import com.barofit.analytics.dto.DailyStatisticResponse;
import com.barofit.analytics.dto.StatisticsSummaryResponse;
import com.barofit.analytics.repository.DailyStatisticRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StatisticsService {

    private final DailyStatisticRepository repository;

    public StatisticsService(DailyStatisticRepository repository) {
        this.repository = repository;
    }

    // 1. 기간별 일일 운동 통계 조회
    public List<DailyStatisticResponse> getDailyStatistics(
            UUID userId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateDateRange(startDate, endDate);

        return repository
                .findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                        userId,
                        startDate,
                        endDate
                )
                .stream()
                .map(DailyStatisticResponse::from)
                .toList();
    }

    // 2. 지정 기간의 운동 통계 합산
    public StatisticsSummaryResponse getStatisticsSummary(
            UUID userId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateDateRange(startDate, endDate);

        List<DailyStatisticResponse> statistics =
                getDailyStatistics(userId, startDate, endDate);

        int sessionCount = 0;
        int totalDurationSeconds = 0;
        int totalReps = 0;

        for (DailyStatisticResponse statistic : statistics) {
            sessionCount = Math.addExact(
                    sessionCount,
                    statistic.sessionCount()
            );

            totalDurationSeconds = Math.addExact(
                    totalDurationSeconds,
                    statistic.totalDurationSeconds()
            );

            totalReps = Math.addExact(
                    totalReps,
                    statistic.totalReps()
            );
        }

        return new StatisticsSummaryResponse(
                sessionCount,
                totalDurationSeconds,
                totalReps
        );
    }

    // 3. 주간 운동 통계 조회 (월요일 ~ 일요일)
    public StatisticsSummaryResponse getWeeklyStatistics(
            UUID userId,
            LocalDate date
    ) {
        if (date == null) {
            throw new IllegalArgumentException(
                    "Date is required"
            );
        }

        LocalDate startDate = date.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );

        LocalDate endDate = date.with(
                TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)
        );

        return getStatisticsSummary(
                userId,
                startDate,
                endDate
        );
    }

    // 4. 월간 운동 통계 조회
    public StatisticsSummaryResponse getMonthlyStatistics(
            UUID userId,
            int year,
            int month
    ) {
        YearMonth yearMonth = YearMonth.of(year, month);

        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        return getStatisticsSummary(
                userId,
                startDate,
                endDate
        );
    }

    // 5. 날짜 범위 유효성 검증
    private void validateDateRange(
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException(
                    "Start date and end date are required"
            );
        }

        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "Start date must not be after end date"
            );
        }
    }
}
