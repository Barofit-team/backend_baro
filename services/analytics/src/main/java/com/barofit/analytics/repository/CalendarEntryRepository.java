
package com.barofit.analytics.repository;

import com.barofit.analytics.entity.CalendarEntry;
import com.barofit.analytics.entity.CalendarEntryId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // PostgreSQL 원자적 갱신
    // 운동 횟수와 총 운동 시간을 DB에서 직접 누적
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO calendar_entries (
                user_id,
                workout_date,
                session_count,
                total_duration_seconds
            )
            VALUES (
                :userId,
                :workoutDate,
                1,
                :durationSeconds
            )
            ON CONFLICT (user_id, workout_date)
            DO UPDATE SET
                session_count =
                    calendar_entries.session_count
                    + EXCLUDED.session_count,
                total_duration_seconds =
                    calendar_entries.total_duration_seconds
                    + EXCLUDED.total_duration_seconds
            """, nativeQuery = true)
    int incrementCalendarEntry(
            @Param("userId") UUID userId,
            @Param("workoutDate") LocalDate workoutDate,
            @Param("durationSeconds") int durationSeconds
    );
}
