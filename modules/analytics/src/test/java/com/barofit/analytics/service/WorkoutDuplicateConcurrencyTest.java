
package com.barofit.analytics.service;

import com.barofit.analytics.entity.WorkoutRecordView;
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
        classes = WorkoutDuplicateConcurrencyTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/barofit_analytics_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.datasource.hikari.maximum-pool-size=12",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class WorkoutDuplicateConcurrencyTest {

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
    void sameSessionSubmittedTenTimesIsCountedOnce()
            throws Exception {

        int requestCount = 10;

        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        OffsetDateTime completedAt =
                OffsetDateTime.parse("2026-10-19T14:00:00+09:00");

        LocalDate workoutDate = LocalDate.of(2026, 10, 19);

        ExecutorService executor =
                Executors.newFixedThreadPool(requestCount);

        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
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

            startSignal.countDown();

            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }

            assertThat(workoutRepository.findById(sessionId))
                    .isPresent();

            assertThat(dailyRepository
                    .findByUserIdAndWorkoutDate(userId, workoutDate)
                    .orElseThrow()
                    .getSessionCount())
                    .isEqualTo(1);

            assertThat(calendarRepository
                    .findByUserIdAndWorkoutDate(userId, workoutDate)
                    .orElseThrow()
                    .getSessionCount())
                    .isEqualTo(1);

            Long recordCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM workout_record_views "
                    + "WHERE session_id = ?",
                    Long.class,
                    sessionId
            );

            assertThat(recordCount).isEqualTo(1L);

        } finally {
            startSignal.countDown();
            executor.shutdown();

            if (!executor.awaitTermination(70, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                throw new IllegalStateException(
                        "Concurrent requests did not finish"
                );
            }

            // 테스트 전용 DB의 해당 사용자 데이터만 정리
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
