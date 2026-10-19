
package com.barofit.analytics.service;

import com.barofit.analytics.entity.CalendarEntry;
import com.barofit.analytics.entity.DailyStatistic;
import com.barofit.analytics.entity.WorkoutRecordView;
import com.barofit.analytics.repository.CalendarEntryRepository;
import com.barofit.analytics.repository.DailyStatisticRepository;
import com.barofit.analytics.repository.WorkoutRecordViewRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = WorkoutCompletionIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/barofit_analytics_test",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Transactional
class WorkoutCompletionIntegrationTest {

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

    @Test
    void completedWorkoutIsSavedInAllThreeTables() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID routineId = UUID.randomUUID();

        OffsetDateTime completedAt =
                OffsetDateTime.parse("2026-10-19T10:00:00+09:00");

        LocalDate workoutDate = LocalDate.of(2026, 10, 19);

        completionService.recordCompletedWorkout(
                sessionId,
                userId,
                routineId,
                completedAt,
                1200,
                45
        );

        WorkoutRecordView workout =
                workoutRepository.findById(sessionId).orElseThrow();

        DailyStatistic daily =
                dailyRepository.findByUserIdAndWorkoutDate(
                        userId, workoutDate
                ).orElseThrow();

        CalendarEntry calendar =
                calendarRepository.findByUserIdAndWorkoutDate(
                        userId, workoutDate
                ).orElseThrow();

        assertThat(workout.getUserId()).isEqualTo(userId);
        assertThat(workout.getRoutineId()).isEqualTo(routineId);
        assertThat(workout.getDurationSeconds()).isEqualTo(1200);
        assertThat(workout.getTotalReps()).isEqualTo(45);

        assertThat(daily.getSessionCount()).isEqualTo(1);
        assertThat(daily.getTotalDurationSeconds()).isEqualTo(1200);
        assertThat(daily.getTotalReps()).isEqualTo(45);

        assertThat(calendar.getSessionCount()).isEqualTo(1);
        assertThat(calendar.getTotalDurationSeconds()).isEqualTo(1200);
    }

    @Test
    void duplicateSessionDoesNotIncreaseStatistics() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        OffsetDateTime completedAt =
                OffsetDateTime.parse("2026-10-19T11:00:00+09:00");

        LocalDate workoutDate = LocalDate.of(2026, 10, 19);

        completionService.recordCompletedWorkout(
                sessionId, userId, null, completedAt, 600, 20
        );

        completionService.recordCompletedWorkout(
                sessionId, userId, null, completedAt, 600, 20
        );

        DailyStatistic daily =
                dailyRepository.findByUserIdAndWorkoutDate(
                        userId, workoutDate
                ).orElseThrow();

        CalendarEntry calendar =
                calendarRepository.findByUserIdAndWorkoutDate(
                        userId, workoutDate
                ).orElseThrow();

        assertThat(daily.getSessionCount()).isEqualTo(1);
        assertThat(daily.getTotalDurationSeconds()).isEqualTo(600);
        assertThat(daily.getTotalReps()).isEqualTo(20);

        assertThat(calendar.getSessionCount()).isEqualTo(1);
        assertThat(calendar.getTotalDurationSeconds()).isEqualTo(600);
    }

    @Test
    void differentSessionsOnSameDayAreAccumulated() {
        UUID userId = UUID.randomUUID();

        OffsetDateTime firstTime =
                OffsetDateTime.parse("2026-10-19T09:00:00+09:00");

        OffsetDateTime secondTime =
                OffsetDateTime.parse("2026-10-19T18:00:00+09:00");

        LocalDate workoutDate = LocalDate.of(2026, 10, 19);

        completionService.recordCompletedWorkout(
                UUID.randomUUID(), userId, null, firstTime, 600, 20
        );

        completionService.recordCompletedWorkout(
                UUID.randomUUID(), userId, null, secondTime, 900, 30
        );

        DailyStatistic daily =
                dailyRepository.findByUserIdAndWorkoutDate(
                        userId, workoutDate
                ).orElseThrow();

        CalendarEntry calendar =
                calendarRepository.findByUserIdAndWorkoutDate(
                        userId, workoutDate
                ).orElseThrow();

        assertThat(daily.getSessionCount()).isEqualTo(2);
        assertThat(daily.getTotalDurationSeconds()).isEqualTo(1500);
        assertThat(daily.getTotalReps()).isEqualTo(50);

        assertThat(calendar.getSessionCount()).isEqualTo(2);
        assertThat(calendar.getTotalDurationSeconds()).isEqualTo(1500);
    }

    @Test
    void negativeDurationIsRejectedWithoutSaving() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        OffsetDateTime completedAt =
                OffsetDateTime.parse("2026-10-19T12:00:00+09:00");

        assertThatThrownBy(() ->
                completionService.recordCompletedWorkout(
                        sessionId, userId, null, completedAt, -1, 10
                )
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(workoutRepository.findById(sessionId)).isEmpty();

        assertThat(dailyRepository.findByUserIdAndWorkoutDate(
                userId, LocalDate.of(2026, 10, 19)
        )).isEmpty();

        assertThat(calendarRepository.findByUserIdAndWorkoutDate(
                userId, LocalDate.of(2026, 10, 19)
        )).isEmpty();
    }
}
