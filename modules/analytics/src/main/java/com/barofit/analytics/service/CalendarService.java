
package com.barofit.analytics.service;

import com.barofit.analytics.dto.CalendarEntryResponse;
import com.barofit.analytics.repository.CalendarEntryRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CalendarService {

    private final CalendarEntryRepository repository;

    public CalendarService(CalendarEntryRepository repository) {
        this.repository = repository;
    }

    public List<CalendarEntryResponse> getMonthlyCalendar(
            UUID userId,
            int year,
            int month
    ) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        return repository
                .findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
                        userId,
                        startDate,
                        endDate
                )
                .stream()
                .map(CalendarEntryResponse::from)
                .toList();
    }
}
