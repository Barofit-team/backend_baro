package com.barofit.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportViewServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ReportSourceClient sources = mock(ReportSourceClient.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ReportService reports = mock(ReportService.class);
    private final ReportViewService service = new ReportViewService(sources, reports,
        jdbc, json, Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneOffset.UTC));

    @Test
    void calendarClassifiesPerformedDaysAndPastPlansUsingSeoulDate() throws Exception {
        String routine = UUID.randomUUID().toString(), plan = UUID.randomUUID().toString();
        var sessions = json.readTree("""
          [
            {"completed":true,"session":{"endedAt":"2026-10-04T15:30:00Z","sets":[{"actualReps":10}]}},
            {"completed":false,"session":{"endedAt":"2026-10-06T03:00:00Z","sets":[{"actualReps":1}]}},
            {"completed":false,"session":{"endedAt":"2026-10-07T03:00:00Z","sets":[]}}
          ]
          """);
        var plans = json.readTree("""
          [{"date":"2026-10-07","planId":"%s","routineId":"%s","routineName":"루틴"},
           {"date":"2026-10-16","planId":"%s","routineId":"%s","routineName":"루틴"}]
          """.formatted(plan, routine, UUID.randomUUID(), routine));
        when(sources.workout(contains("/history?"), eq("Bearer test"))).thenReturn(sessions);
        when(sources.routine(contains("/month?"), eq("Bearer test"))).thenReturn(plans);
        when(sources.workout(eq("/api/v1/workout-sessions/streak"), eq("Bearer test")))
            .thenReturn(json.valueToTree(Map.of("streakDays", 4)));

        var result = service.calendar(2026, 10, "Bearer test");
        assertThat(result.days().get(4).status()).isEqualTo("COMPLETED");
        assertThat(result.days().get(5).status()).isEqualTo("PARTIAL");
        assertThat(result.days().get(6).status()).isEqualTo("MISSED");
        assertThat(result.days().get(15).status()).isEqualTo("PLANNED");
        assertThat(result.completedDays()).isEqualTo(1);
        assertThat(result.partialDays()).isEqualTo(1);
        assertThat(result.missedDays()).isEqualTo(1);
        assertThat(result.streakDays()).isEqualTo(4);
    }

    @Test
    void scoresAggregateItemsAcrossSetsWithEqualItemWeightAndIgnoreUnknown() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var exercise = json.readTree("{\"sets\":[{\"id\":\"" + a + "\"},{\"id\":\"" + b + "\"}]}");
        var done = json.readTree("{\"actualReps\":2}");
        Map<String, com.fasterxml.jackson.databind.JsonNode> results = Map.of(a.toString(), done, b.toString(), done);
        var first = List.of(new com.barofit.report.repository.ReportRepository.ScoreItem(a,"a","repetition","repetitions",1,0),
            new com.barofit.report.repository.ReportRepository.ScoreItem(a,"b","repetition","repetitions",0,1),
            new com.barofit.report.repository.ReportRepository.ScoreItem(a,"unknown","repetition","repetitions",0,0));
        var second = List.of(new com.barofit.report.repository.ReportRepository.ScoreItem(b,"a","repetition","repetitions",1,8),
            new com.barofit.report.repository.ReportRepository.ScoreItem(b,"b","repetition","repetitions",1,0));
        assertThat(ReportViewService.exerciseScore(exercise, results, Map.of(a, first), true)).isNull();
        assertThat(ReportViewService.exerciseScore(exercise, results, Map.of(a, first, b, second), false)).isNull();
        assertThat(ReportViewService.exerciseScore(exercise, results, Map.of(a, first, b, second), true)).isEqualTo(35.0);
        assertThat(ReportViewService.exerciseScore(exercise, results, Map.of(a, List.of(first.get(2)), b, List.of()), true)).isNull();
    }

    @Test
    void routineScoreRequiresEveryPlannedExerciseScore() throws Exception {
        UUID user = UUID.randomUUID(), session = UUID.randomUUID(), routine = UUID.randomUUID();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var data = json.readTree("""
            {"routineId":"%s","status":"COMPLETED","startedAt":"2026-10-15T00:00:00Z",
             "endedAt":"2026-10-15T00:30:00Z","sets":[{"routineSetId":"%s","actualReps":1},{"routineSetId":"%s","actualReps":1}],
             "routineSnapshot":{"name":"전신","exercises":[
               {"exerciseId":"pushup","name":"푸시업","sets":[{"id":"%s"}]},
               {"exerciseId":"plank","name":"플랭크","sets":[{"id":"%s"}]}
             ]}}
            """.formatted(routine, a, b, a, b));
        when(sources.workout(eq("/api/v1/workout-sessions/" + session), anyString())).thenReturn(data);
        when(reports.getSessionSummary(user, session)).thenReturn(Map.of());
        var one = new com.barofit.report.repository.ReportRepository.ScoreItem(a,"a","time","seconds",8,2);
        var two = new com.barofit.report.repository.ReportRepository.ScoreItem(b,"b","time","seconds",6,4);
        when(reports.scoreItems(user, session)).thenReturn(List.of(one), List.of(one, two));
        var incomplete = service.result(user, session, "Bearer test");
        assertThat(incomplete.routineScore()).isNull();
        assertThat(incomplete.scoredExercises()).isEqualTo(1);
        var complete = service.result(user, session, "Bearer test");
        assertThat(complete.routineScore()).isEqualTo(70.0);
        assertThat(complete.exercises().get(0).postureScore()).isEqualTo(80.0);
    }
}
