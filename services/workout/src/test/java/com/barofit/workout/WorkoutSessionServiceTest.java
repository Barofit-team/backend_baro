package com.barofit.workout;

import com.barofit.WorkoutApplication;
import com.barofit.common.error.BusinessException;
import com.barofit.workout.WorkoutDtos.*;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = WorkoutApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Transactional
class WorkoutSessionServiceTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18.6");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("test").generate();
        Path jwks = Files.createTempFile("barofit-workout-test-", ".json");
        Files.writeString(jwks, new JWKSet(key.toPublicJWK()).toString());
        jwks.toFile().deleteOnExit();
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("barofit.jwt.public-jwks-path", jwks::toString);
    }

    @Autowired WorkoutSessionService service;
    @Autowired WeeklyWorkoutProgressService progress;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean RoutineSnapshotClient routines;

    @Test
    void legacyRequestSerializationDoesNotChangeWhenRestIsMissing() {
        var request = new SyncRequest(UUID.randomUUID(), json.readTree("{}"), SessionStatus.ACTIVE, 0L,
            Instant.parse("2026-10-20T09:00:00Z"), null, 0, List.of());
        assertThat(json.writeValueAsString(request)).doesNotContain("restDurationSeconds");
    }

    @Test
    void offlineCompletionCanBeRetriedButNotChanged() {
        UUID user = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID routineId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();
        Instant start = Instant.parse("2026-10-20T09:00:00Z");
        var snapshot = json.readTree("""
            {"id":"%s","type":"USER","exercises":[{"exerciseId":"push-up","sets":[{"id":"%s"}]}]}
            """.formatted(routineId, setId));
        when(routines.fetch(eq(routineId), eq("USER"), eq("test-token"))).thenReturn(snapshot);
        SetResult set = new SetResult(setId, 12, null, List.of("HIP_DROP"), Map.of("HIP_DROP", 10),
            "골반을 곧게", "mobile-stream-1.0", "sample-model", 92.8, start.plusSeconds(30));
        SyncRequest request = new SyncRequest(routineId, snapshot, SessionStatus.COMPLETED, 3L,
            start, start.plusSeconds(60), 45, List.of(set), 10);

        assertThat(service.sync(user, sessionId, request, "test-token").sets()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workout.analytics_completion_outbox WHERE session_id=?",
            Integer.class, sessionId)).isEqualTo(1);
        assertThat(service.get(user, sessionId).sets().getFirst().errorCounts()).containsEntry("HIP_DROP", 10);
        assertThat(service.get(user, sessionId).restDurationSeconds()).isEqualTo(10);
        assertThat(service.sync(user, sessionId, request, "test-token").sets()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workout.analytics_completion_outbox WHERE session_id=?",
            Integer.class, sessionId)).isEqualTo(1);
        assertThat(service.get(user, sessionId).status()).isEqualTo(SessionStatus.COMPLETED);
        assertThatThrownBy(() -> service.get(UUID.randomUUID(), sessionId))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.sync(user, sessionId, new SyncRequest(routineId, snapshot,
            SessionStatus.COMPLETED, 4L, start, start.plusSeconds(60), 45, List.of(set)), "test-token"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void pauseAndResumeRejectStaleRevision() {
        UUID user = UUID.randomUUID(), sessionId = UUID.randomUUID(), routineId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();
        Instant start = Instant.parse("2026-10-20T09:00:00Z");
        var snapshot = json.readTree("""
            {"id":"%s","type":"USER","exercises":[{"exerciseId":"plank","sets":[{"id":"%s"}]}]}
            """.formatted(routineId, setId));
        when(routines.fetch(eq(routineId), eq("USER"), eq("test-token"))).thenReturn(snapshot);
        SyncRequest active = new SyncRequest(routineId, snapshot, SessionStatus.ACTIVE, 0L, start, null, 0, List.of());
        service.sync(user, sessionId, active, "test-token");
        service.sync(user, sessionId, new SyncRequest(routineId, snapshot, SessionStatus.PAUSED, 1L,
            start, null, 20, List.of()), "test-token");
        assertThat(service.get(user, sessionId).status()).isEqualTo(SessionStatus.PAUSED);
        assertThatThrownBy(() -> service.sync(user, sessionId, active, "test-token")).isInstanceOf(BusinessException.class);
        var changedSnapshot = json.readTree("""
            {"id":"%s","type":"USER","name":"수정됨","exercises":[{"exerciseId":"plank","sets":[{"id":"%s"}]}]}
            """.formatted(routineId, setId));
        assertThatThrownBy(() -> service.sync(user, sessionId, new SyncRequest(routineId, changedSnapshot,
            SessionStatus.ACTIVE, 2L, start, null, 20, List.of()), "test-token")).isInstanceOf(BusinessException.class);
        service.sync(user, sessionId, new SyncRequest(routineId, snapshot, SessionStatus.ACTIVE, 2L,
            start, null, 20, List.of()), "test-token");
        assertThat(service.get(user, sessionId).status()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    void weeklyProgressUsesFinishedSessionSetThresholdAndSeoulEndDate() {
        UUID user = UUID.randomUUID(), routineId = UUID.randomUUID();
        UUID[] ids = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        Instant start = Instant.parse("2026-10-18T14:55:00Z");
        var snapshot = json.readTree("""
            {"id":"%s","type":"USER","exercises":[{"sets":[{"id":"%s"},{"id":"%s"},{"id":"%s"}]}]}
            """.formatted(routineId, ids[0], ids[1], ids[2]));
        when(routines.fetch(eq(routineId), eq("USER"), eq("test-token"))).thenReturn(snapshot);
        var first = new SetResult(ids[0], 10, null, List.of(), Map.of(), null, null, null, null,
            start.plusSeconds(30));
        var second = new SetResult(ids[1], 10, null, List.of(), Map.of(), null, null, null, null,
            start.plusSeconds(60));
        var third = new SetResult(ids[2], null, 20, List.of(), Map.of(), null, null, null, null,
            start.plusSeconds(90));
        LocalDate week = LocalDate.parse("2026-10-19");
        service.sync(user, UUID.randomUUID(), new SyncRequest(routineId, snapshot, SessionStatus.ACTIVE, 0L,
            start, null, 90, List.of(first, second, third)), "test-token");
        assertThat(progress.get(user, week).completedDates()).isEmpty();
        service.sync(user, UUID.randomUUID(), new SyncRequest(routineId, snapshot, SessionStatus.ABANDONED, 0L,
            start, start.plusSeconds(600), 90, List.of(first, second)), "test-token");
        assertThat(progress.get(user, week).completedDates()).isEmpty();
        service.sync(user, UUID.randomUUID(), new SyncRequest(routineId, snapshot, SessionStatus.COMPLETED, 0L,
            start, start.plusSeconds(600), 90, List.of(first, second, third)), "test-token");
        service.sync(user, UUID.randomUUID(), new SyncRequest(routineId, snapshot, SessionStatus.COMPLETED, 0L,
            start, start.plusSeconds(660), 90, List.of(first, second, third)), "test-token");
        assertThat(progress.get(user, week).completedDates()).containsExactly(week);
        assertThat(progress.get(UUID.randomUUID(), week).completedDates()).isEmpty();
    }

    @Test
    void threeExerciseRoutineNeedsTwoFullyPerformedExercises() {
        UUID user = UUID.randomUUID(), routineId = UUID.randomUUID();
        UUID[] ids = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        Instant start = Instant.parse("2026-10-20T09:00:00Z");
        var snapshot = json.readTree("""
            {"id":"%s","type":"USER","exercises":[
                {"exerciseId":"push-up","sets":[{"id":"%s"},{"id":"%s"}]},
                {"exerciseId":"squat","sets":[{"id":"%s"}]},
                {"exerciseId":"plank","sets":[{"id":"%s"}]}
            ]}
            """.formatted(routineId, ids[0], ids[1], ids[2], ids[3]));
        when(routines.fetch(eq(routineId), eq("USER"), eq("test-token"))).thenReturn(snapshot);
        SetResult first = new SetResult(ids[0], 10, null, List.of(), Map.of(), null, null, null, null,
            start.plusSeconds(30));
        SetResult second = new SetResult(ids[1], 10, null, List.of(), Map.of(), null, null, null, null,
            start.plusSeconds(60));
        SetResult third = new SetResult(ids[2], 10, null, List.of(), Map.of(), null, null, null, null,
            start.plusSeconds(90));
        LocalDate week = LocalDate.parse("2026-10-19");
        UUID incomplete = UUID.randomUUID();
        service.sync(user, incomplete, new SyncRequest(routineId, snapshot, SessionStatus.COMPLETED, 0L,
            start, start.plusSeconds(600), 90, List.of(first, third)), "test-token");
        assertThat(progress.get(user, week).completedDates()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workout.analytics_completion_outbox WHERE session_id=?",
            Integer.class, incomplete)).isZero();

        UUID complete = UUID.randomUUID();
        service.sync(user, complete, new SyncRequest(routineId, snapshot, SessionStatus.COMPLETED, 0L,
            start, start.plusSeconds(600), 90, List.of(first, second, third)), "test-token");
        assertThat(progress.get(user, week).completedDates()).containsExactly(LocalDate.parse("2026-10-20"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workout.analytics_completion_outbox WHERE session_id=?",
            Integer.class, complete)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rejectsClientSnapshotThatDiffersFromOwnedRoutine() {
        UUID user = UUID.randomUUID(), sessionId = UUID.randomUUID(), routineId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();
        var forged = json.readTree("""
            {"id":"%s","type":"USER","name":"변조된 이름","exercises":[{"sets":[{"id":"%s"}]}]}
            """.formatted(routineId, setId));
        var source = json.readTree("""
            {"id":"%s","type":"USER","name":"원본 이름","exercises":[{"sets":[{"id":"%s"}]}]}
            """.formatted(routineId, setId));
        when(routines.fetch(eq(routineId), eq("USER"), eq("test-token"))).thenReturn(source);
        var request = new SyncRequest(routineId, forged, SessionStatus.ACTIVE, 0L,
            Instant.parse("2026-10-20T09:00:00Z"), null, 0, List.of());

        assertThatThrownBy(() -> service.sync(user, sessionId, request, "test-token"))
            .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workout.workout_session WHERE id=?",
            Integer.class, sessionId)).isZero();
    }

    @Test
    void acceptsSameRoutineWithNormalizedJsonNumber() {
        UUID user = UUID.randomUUID(), sessionId = UUID.randomUUID(), routineId = UUID.randomUUID();
        UUID setId = UUID.randomUUID();
        var source = json.readTree("""
            {"id":"%s","type":"TEMPLATE","createdAt":"2026-10-20T09:00:00.123456Z","updatedAt":"2026-10-20T09:00:00.123456Z","exercises":[{"sets":[{"id":"%s","weightKg":0.0}]}]}
            """.formatted(routineId, setId));
        var client = json.readTree("""
            {"id":"%s","type":"TEMPLATE","createdAt":"2026-10-20T09:00:00.123456789Z","updatedAt":"2026-10-20T09:00:00.123456789Z","exercises":[{"sets":[{"id":"%s","weightKg":0}]}]}
            """.formatted(routineId, setId));
        when(routines.fetch(eq(routineId), eq("TEMPLATE"), eq("test-token"))).thenReturn(source);
        var request = new SyncRequest(routineId, client, SessionStatus.ACTIVE, 0L,
            Instant.parse("2026-10-20T09:00:00Z"), null, 0, List.of());
        assertThat(service.sync(user, sessionId, request, "test-token").status())
            .isEqualTo(SessionStatus.ACTIVE);
    }
}
