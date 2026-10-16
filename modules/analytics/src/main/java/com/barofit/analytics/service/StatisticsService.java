
package com.barofit.analytics.service;

import com.barofit.analytics.dto.DailyStatisticResponse;
import com.barofit.analytics.repository.DailyStatisticRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StatisticsService {

    private final DailyStatisticRepository repository;

    public StatisticsService(DailyStatisticRepository repository) {
        this.repository = repository;
    }

    public List<DailyStatisticResponse> getDailyStatistics(
            UUID userId,
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
}
