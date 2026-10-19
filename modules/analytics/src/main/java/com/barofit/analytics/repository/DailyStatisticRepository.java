
package com.barofit.analytics.repository;

import com.barofit.analytics.entity.DailyStatistic;
import com.barofit.analytics.entity.DailyStatisticId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyStatisticRepository
        extends JpaRepository<DailyStatistic, DailyStatisticId> {

    // 특정 사용자의 기간별 일일 통계 조회
    List<DailyStatistic> findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(
            UUID userId,
            LocalDate startDate,
            LocalDate endDate
    );

    // 특정 사용자의 특정 날짜 통계 조회
    Optional<DailyStatistic> findByUserIdAndWorkoutDate(
            UUID userId,
            LocalDate workoutDate
    );

    // PostgreSQL 원자적 갱신
    // 같은 사용자와 날짜에 여러 운동이 기록되어도
    // DB에서 운동 횟수, 운동 시간, 반복 횟수를 누적한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO daily_statistics (
                user_id,
                workout_date,
                session_count,
                total_duration_seconds,
                total_reps
            )
            VALUES (
                :userId,
                :workoutDate,
                1,
                :durationSeconds,
                :totalReps
            )
            ON CONFLICT (user_id, workout_date)
            DO UPDATE SET
                session_count =
                    daily_statistics.session_count
                    + EXCLUDED.session_count,
                total_duration_seconds =
                    daily_statistics.total_duration_seconds
                    + EXCLUDED.total_duration_seconds,
                total_reps =
                    daily_statistics.total_reps
                    + EXCLUDED.total_reps
            """, nativeQuery = true)
    int incrementWorkoutStatistics(
            @Param("userId") UUID userId,
            @Param("workoutDate") LocalDate workoutDate,
            @Param("durationSeconds") int durationSeconds,
            @Param("totalReps") int totalReps
    );
}
