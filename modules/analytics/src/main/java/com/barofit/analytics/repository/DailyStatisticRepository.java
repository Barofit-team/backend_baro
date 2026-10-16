
package com.barofit.analytics.repository;

import com.barofit.analytics.entity.DailyStatistic;
import com.barofit.analytics.entity.DailyStatisticId;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
