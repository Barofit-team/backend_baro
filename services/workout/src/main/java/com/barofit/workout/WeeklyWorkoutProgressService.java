package com.barofit.workout;

import com.barofit.common.error.BusinessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WeeklyWorkoutProgressService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public WeeklyWorkoutProgressService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Result(LocalDate weekStart, List<LocalDate> completedDates) { }
    public record StreakResult(LocalDate asOf, int streakDays) { }

    public StreakResult currentStreak(UUID userId) {
        LocalDate today = LocalDate.now(SEOUL);
        Map<LocalDate, Set<LocalDate>> cache = new HashMap<>();
        java.util.function.Predicate<LocalDate> completed = date -> {
            LocalDate monday = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return cache.computeIfAbsent(monday, key -> new HashSet<>(get(userId, key).completedDates())).contains(date);
        };
        LocalDate cursor = completed.test(today) ? today : today.minusDays(1);
        int count = 0;
        while (completed.test(cursor)) { count++; cursor = cursor.minusDays(1); }
        return new StreakResult(today, count);
    }

    public Result get(UUID userId, LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY)
            throw new BusinessException(WorkoutErrorCode.INVALID_WORKOUT, "weekStart는 월요일이어야 합니다.");
        Instant from = weekStart.atStartOfDay(SEOUL).toInstant();
        Instant until = weekStart.plusDays(7).atStartOfDay(SEOUL).toInstant();
        List<Session> sessions = jdbc.query("""
            SELECT s.id, s.ended_at, s.routine_snapshot
            FROM workout.workout_session s
            WHERE s.user_id = ? AND s.status IN ('COMPLETED','ABANDONED')
                AND s.ended_at >= ? AND s.ended_at < ?
            """, (rs, row) -> new Session(
                (UUID) rs.getObject("id"),
                rs.getTimestamp("ended_at").toInstant().atZone(SEOUL).toLocalDate(),
                rs.getString("routine_snapshot")),
            userId, java.sql.Timestamp.from(from), java.sql.Timestamp.from(until));
        SortedSet<LocalDate> dates = new TreeSet<>();
        for (Session session : sessions) {
            JsonNode snapshot = json.readTree(session.snapshot());
            Set<String> performedSetIds = new HashSet<>(jdbc.query("""
                SELECT routine_set_id FROM workout.workout_set_result
                WHERE session_id = ? AND (actual_reps > 0 OR actual_duration_seconds > 0)
                """, (rs, row) -> rs.getObject("routine_set_id").toString(), session.id()));
            if (WorkoutCompletionPolicy.qualifies(snapshot, performedSetIds))
                dates.add(session.date());
        }
        return new Result(weekStart, List.copyOf(dates));
    }

    private record Session(UUID id, LocalDate date, String snapshot) { }
}
