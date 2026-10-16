
package com.barofit.analytics.repository;

import com.barofit.analytics.entity.CalendarEntry;
import com.barofit.analytics.entity.CalendarEntryId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarEntryRepository
        extends JpaRepository<CalendarEntry, CalendarEntryId> {

    // 특정 사용자의 기간별 캘린더 조회
    List<CalendarEntry> findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
            UUID userId,
            LocalDate startDate,
            LocalDate endDate
    );

    // 특정 사용자의 날짜별 캘린더 조회
    Optional<CalendarEntry> findByUserIdAndWorkoutDate(
            UUID userId,
            LocalDate workoutDate
    );
}
