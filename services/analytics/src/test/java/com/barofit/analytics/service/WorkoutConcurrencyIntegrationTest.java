
package com.barofit.analytics.service;

import com.barofit.analytics.entity.WorkoutRecordView;
import com.barofit.analytics.entity.DailyStatistic;
import com.barofit.analytics.entity.CalendarEntry;

import com.barofit.analytics.repository.WorkoutRecordViewRepository;
import com.barofit.analytics.repository.DailyStatisticRepository;
import com.barofit.analytics.repository.CalendarEntryRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = WorkoutConcurrencyIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/barofit_analytics_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.datasource.hikari.maximum-pool-size=12",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class WorkoutConcurrencyIntegrationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = WorkoutRecordView.class)
    @EnableJpaRepositories(
            basePackageClasses = WorkoutRecordViewRepository.class
    )
    @Import(WorkoutCompletionService.class)
    static class TestApplication {
    }

    @Autowired
    private WorkoutCompletionService completionService;

    @Autowired
    private WorkoutRecordViewRepository workoutRepository;

    @Autowired
    private DailyStatisticRepository dailyRepository;

    @Autowired
    private CalendarEntryRepository calendarRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void tenConcurrentWorkoutsAreAllCounted() throws Exception {

        int workoutCount = 10;

        UUID userId = UUID.randomUUID();
        LocalDate workoutDate = LocalDate.of(2026, 10, 19);

        OffsetDateTime completedAt =
                OffsetDateTime.parse("2026-10-19T12:00:00+09:00");

        ExecutorService executor =
                Executors.newFixedThreadPool(workoutCount);

        CountDownLatch startSignal = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < workoutCount; i++) {

                UUID sessionId = UUID.randomUUID();

                futures.add(executor.submit(() -> {
                    startSignal.await();

                    completionService.recordCompletedWorkout(
                            sessionId,
                            userId,
                            null,
                            completedAt,
                            600,
                            20
                    );

                    return null;
                }));
            }

            // 준비된 10개 작업을 동시에 시작한다.
            startSignal.countDown();

            // 모든 작업이 완료됐는지 확인한다.
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }

            long savedWorkoutCount = workoutRepository
                    .findByUserIdOrderByCompletedAtDesc(
                            userId,
                            PageRequest.of(0, 20)
                    )
                    .getTotalElements();

            DailyStatistic daily = dailyRepository
                    .findByUserIdAndWorkoutDate(userId, workoutDate)
                    .orElseThrow();

            CalendarEntry calendar = calendarRepository
                    .findByUserIdAndWorkoutDate(userId, workoutDate)
                    .orElseThrow();

            // 운동 기록 10개가 모두 저장됐는지 확인
            assertThat(savedWorkoutCount).isEqualTo(10);

            // 일별 운동 통계 확인
            assertThat(daily.getSessionCount()).isEqualTo(10);
            assertThat(daily.getTotalDurationSeconds())
                    .isEqualTo(6000);
            assertThat(daily.getTotalReps()).isEqualTo(200);

            // 운동 캘린더 확인
            assertThat(calendar.getSessionCount()).isEqualTo(10);
            assertThat(calendar.getTotalDurationSeconds())
                    .isEqualTo(6000);

        } finally {
            // 작업 종료를 기다린 후 테스트 데이터를 정리한다.
            startSignal.countDown();
            executor.shutdown();

            if (!executor.awaitTermination(70, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                throw new IllegalStateException(
                        "Concurrent workout tasks did not finish"
                );
            }

            // 별도 테스트 DB에서 이 테스트 사용자의 데이터만 삭제
            jdbcTemplate.update(
                    "DELETE FROM calendar_entries WHERE user_id = ?",
                    userId
            );

            jdbcTemplate.update(
                    "DELETE FROM daily_statistics WHERE user_id = ?",
                    userId
            );

            jdbcTemplate.update(
                    "DELETE FROM workout_record_views WHERE user_id = ?",
                    userId
            );
        }
    }
}
