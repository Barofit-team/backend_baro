package com.barofit.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.barofit.report.repository.ReportRepository.ScoreItem;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportViewService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final ReportSourceClient sources;
    private final ReportService reports;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ReportViewService(ReportSourceClient sources, ReportService reports, JdbcTemplate jdbc,
                             ObjectMapper mapper, Clock clock) {
        this.sources = sources; this.reports = reports; this.jdbc = jdbc; this.mapper = mapper; this.clock = clock;
    }

    public record Day(LocalDate date, String status, UUID planId, UUID routineId, String routineName,
                      int sessionCount) { }
    public record Month(int year, int month, List<Day> days, long completedDays,
                        long partialDays, long missedDays, int streakDays) { }
    public record RecordCard(LocalDate date, UUID sessionId, UUID routineId, String routineName,
                             String status, int completedSets, int plannedSets, int durationSeconds) { }
    public record RecordPage(List<RecordCard> content, int page, int size, boolean hasNext) { }
    public record Feedback(String difficulty, List<String> discomfortAreas) { }
    public record ExerciseResult(String exerciseId, String name, int plannedSets, int completedSets,
                                 int actualReps, int actualDurationSeconds, List<String> feedbackCodes,
                                 List<String> deviceFeedback, Double postureScore) { }
    public record WorkoutResult(UUID sessionId, UUID routineId, String routineName, String status,
                                int plannedSets, int completedSets, int totalReps, int activeDurationSeconds,
                                Long elapsedSeconds, Integer restSeconds, List<ExerciseResult> exercises,
                                Double routineScore, int scoredExercises, int plannedExercises,
                                Map<String, Object> postureSummary, Feedback feedback) { }
    public record ScoreHistory(UUID sessionId, double score, Instant completedAt) { }

    private YearMonth month(int year, int month) {
        try { return YearMonth.of(year, month); }
        catch (DateTimeException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month"); }
    }

    private JsonNode history(YearMonth month, String auth) {
        JsonNode result = sources.workout("/api/v1/workout-sessions/history?startDate=" + month.atDay(1)
            + "&endDate=" + month.atEndOfMonth(), auth);
        if (result == null || !result.isArray())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Invalid workout history response");
        return result;
    }

    private JsonNode plans(YearMonth month, String auth) {
        JsonNode result = sources.routine("/api/v1/workout-plans/month?year=" + month.getYear()
            + "&month=" + month.getMonthValue(), auth);
        if (result == null || !result.isArray())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Invalid workout plan response");
        return result;
    }

    public Month calendar(int year, int month, String auth) {
        YearMonth period = month(year, month);
        JsonNode sessions = history(period, auth), plans = plans(period, auth);
        Map<LocalDate, JsonNode> byPlan = new HashMap<>();
        for (JsonNode p : plans) byPlan.put(LocalDate.parse(p.path("date").asText()), p);
        Map<LocalDate, List<JsonNode>> byDate = new HashMap<>();
        for (JsonNode item : sessions) {
            LocalDate date = Instant.parse(item.path("session").path("endedAt").asText()).atZone(SEOUL).toLocalDate();
            byDate.computeIfAbsent(date, ignored -> new ArrayList<>()).add(item);
        }
        List<Day> days = new ArrayList<>();
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        for (int i = 1; i <= period.lengthOfMonth(); i++) {
            LocalDate date = period.atDay(i);
            List<JsonNode> entries = byDate.getOrDefault(date, List.of());
            JsonNode plan = byPlan.get(date);
            boolean completed = entries.stream().anyMatch(e -> e.path("completed").asBoolean());
            boolean performed = entries.stream().anyMatch(e -> performedSets(e.path("session").path("sets")) > 0);
            String status = completed ? "COMPLETED" : performed ? "PARTIAL"
                : plan != null ? (date.isBefore(today) ? "MISSED" : "PLANNED") : "NONE";
            days.add(new Day(date, status, plan == null ? null : UUID.fromString(plan.path("planId").asText()),
                plan == null ? null : UUID.fromString(plan.path("routineId").asText()),
                plan == null ? null : plan.path("routineName").asText(), entries.size()));
        }
        JsonNode streak = sources.workout("/api/v1/workout-sessions/streak", auth);
        if (streak == null || !streak.path("streakDays").isInt())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Invalid workout streak response");
        return new Month(year, month, List.copyOf(days), days.stream().filter(d -> d.status().equals("COMPLETED")).count(),
            days.stream().filter(d -> d.status().equals("PARTIAL")).count(),
            days.stream().filter(d -> d.status().equals("MISSED")).count(), streak.path("streakDays").asInt());
    }

    public RecordPage records(int year, int month, int page, int size, String auth) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        YearMonth period = month(year, month);
        List<RecordCard> cards = cards(period, auth);
        int from = Math.min(cards.size(), page * size), until = Math.min(cards.size(), from + size);
        return new RecordPage(List.copyOf(cards.subList(from, until)), page, size, until < cards.size());
    }

    public List<RecordCard> dateRecords(LocalDate date, String auth) {
        return cards(YearMonth.from(date), auth).stream().filter(card -> card.date().equals(date)).toList();
    }

    private List<RecordCard> cards(YearMonth period, String auth) {
        List<RecordCard> cards = new ArrayList<>();
        Set<LocalDate> performedDates = new HashSet<>();
        for (JsonNode item : history(period, auth)) {
            JsonNode s = item.path("session"), snapshot = s.path("routineSnapshot");
            int performed = performedSets(s.path("sets"));
            if (performed == 0) continue;
            LocalDate date = Instant.parse(s.path("endedAt").asText()).atZone(SEOUL).toLocalDate();
            performedDates.add(date);
            cards.add(new RecordCard(date,
                UUID.fromString(s.path("id").asText()), UUID.fromString(s.path("routineId").asText()),
                snapshot.path("name").asText(), item.path("completed").asBoolean() ? "COMPLETED" : "PARTIAL",
                performed, plannedSets(snapshot), s.path("activeDurationSeconds").asInt()));
        }
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        for (JsonNode plan : plans(period, auth)) {
            LocalDate date = LocalDate.parse(plan.path("date").asText());
            if (date.isBefore(today) && !performedDates.contains(date))
                cards.add(new RecordCard(date, null, UUID.fromString(plan.path("routineId").asText()),
                    plan.path("routineName").asText(), "MISSED", 0, 0, 0));
        }
        cards.sort(Comparator.comparing(RecordCard::date).reversed());
        return cards;
    }

    public WorkoutResult result(UUID userId, UUID sessionId, String auth) {
        JsonNode session = sources.workout("/api/v1/workout-sessions/" + sessionId, auth);
        return resultForSession(userId, sessionId, session);
    }

    private WorkoutResult resultForSession(UUID userId, UUID sessionId, JsonNode session) {
        JsonNode snapshot = session.path("routineSnapshot"), sets = session.path("sets");
        Map<UUID, List<ScoreItem>> scoreItems = new HashMap<>();
        for (ScoreItem item : reports.scoreItems(userId, sessionId))
            scoreItems.computeIfAbsent(item.routineSetId(), ignored -> new ArrayList<>()).add(item);
        Map<String, JsonNode> results = new HashMap<>();
        for (JsonNode set : sets) results.put(set.path("routineSetId").asText(), set);
        List<ExerciseResult> exercises = new ArrayList<>();
        int totalReps = 0, scored = 0, plannedExercises = 0;
        double scoreTotal = 0;
        for (JsonNode exercise : snapshot.path("exercises")) {
            int completed = 0, reps = 0, duration = 0;
            Set<String> codes = new LinkedHashSet<>(), feedback = new LinkedHashSet<>();
            for (JsonNode planned : exercise.path("sets")) {
                JsonNode set = results.get(planned.path("id").asText());
                if (set == null) continue;
                if (performed(set)) completed++;
                reps += set.path("actualReps").asInt(0);
                duration += set.path("actualDurationSeconds").asInt(0);
                for (JsonNode code : set.path("feedbackCodes")) codes.add(code.asText());
                if (!set.path("feedbackText").asText("").isBlank()) feedback.add(set.path("feedbackText").asText());
            }
            totalReps += reps;
            Double postureScore = exerciseScore(exercise, results, scoreItems,
                session.path("endedAt").isTextual() && Set.of("COMPLETED", "ABANDONED").contains(session.path("status").asText()));
            if (!exercise.path("sets").isEmpty()) {
                plannedExercises++;
                if (postureScore != null) { scored++; scoreTotal += postureScore; }
            }
            exercises.add(new ExerciseResult(exercise.path("exerciseId").asText(), exercise.path("name").asText(),
                exercise.path("sets").size(), completed, reps, duration, List.copyOf(codes), List.copyOf(feedback), postureScore));
        }
        Long elapsed = session.path("endedAt").isTextual() ? Duration.between(
            Instant.parse(session.path("startedAt").asText()), Instant.parse(session.path("endedAt").asText())).getSeconds() : null;
        return new WorkoutResult(sessionId, UUID.fromString(session.path("routineId").asText()),
            snapshot.path("name").asText(), session.path("status").asText(), plannedSets(snapshot), performedSets(sets),
            totalReps, session.path("activeDurationSeconds").asInt(), elapsed,
            session.path("restDurationSeconds").isInt() ? session.path("restDurationSeconds").asInt() : null, List.copyOf(exercises),
            plannedExercises > 0 && scored == plannedExercises ? scoreTotal / plannedExercises : null, scored, plannedExercises,
            reports.getSessionSummary(userId, sessionId), feedback(userId, sessionId));
    }

    /** Equal item weights; each item is aggregated over all performed sets before its percentage is calculated. */
    static Double exerciseScore(JsonNode exercise, Map<String, JsonNode> results,
                                Map<UUID, List<ScoreItem>> scoreItems, boolean finished) {
        if (!finished) return null;
        Map<String, double[]> totals = new HashMap<>();
        Map<String, String> bases = new HashMap<>();
        int performedCount = 0;
        for (JsonNode planned : exercise.path("sets")) {
            JsonNode result = results.get(planned.path("id").asText());
            if (result == null || !performed(result)) continue;
            performedCount++;
            List<ScoreItem> items = scoreItems.get(UUID.fromString(planned.path("id").asText()));
            if (items == null) return null;
            for (ScoreItem item : items) {
                if (item.itemId() == null) continue;
                String basis = item.basis() + ":" + item.unit();
                String previous = bases.putIfAbsent(item.itemId(), basis);
                if (previous != null && !previous.equals(basis)) return null;
                double[] value = totals.computeIfAbsent(item.itemId(), ignored -> new double[2]);
                value[0] += item.compliant(); value[1] += item.warning();
            }
        }
        if (performedCount == 0) return null;
        return totals.values().stream().filter(v -> v[0] + v[1] > 0)
            .mapToDouble(v -> 100.0 * v[0] / (v[0] + v[1])).average().stream().boxed().findFirst().orElse(null);
    }

    public List<ScoreHistory> scoreHistory(UUID userId, String exerciseId, int limit, String auth) {
        if (exerciseId == null || !exerciseId.matches("[a-z][a-z0-9_-]{0,49}") || limit < 1 || limit > 30)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid score history request");
        String normalized = exerciseId.replace("-", "").replace("_", "");
        List<UUID> sessions = jdbc.query("""
            SELECT DISTINCT session_id FROM exercise_set_results
            WHERE user_id=? AND routine_set_id IS NOT NULL AND replace(replace(exercise,'-',''),'_','')=?
            """, (rs, row) -> rs.getObject("session_id", UUID.class), userId, normalized);
        List<ScoreHistory> history = new ArrayList<>();
        for (UUID id : sessions) {
            JsonNode session = sources.workout("/api/v1/workout-sessions/" + id, auth);
            if (!session.path("endedAt").isTextual()) continue;
            WorkoutResult result = resultForSession(userId, id, session);
            for (ExerciseResult exercise : result.exercises()) {
                if (exercise.exerciseId().replace("-", "").replace("_", "").equals(normalized)
                    && exercise.postureScore() != null)
                    history.add(new ScoreHistory(id, exercise.postureScore(), Instant.parse(session.path("endedAt").asText())));
            }
        }
        history.sort(Comparator.comparing(ScoreHistory::completedAt).reversed().thenComparing(ScoreHistory::sessionId));
        return history.stream().limit(limit).toList();
    }

    public Feedback feedback(UUID userId, UUID sessionId) {
        return jdbc.query("SELECT difficulty, discomfort_areas::text AS areas FROM workout_feedback WHERE user_id=? AND session_id=?",
            rs -> {
                if (!rs.next()) return null;
                try { return new Feedback(rs.getString("difficulty"), mapper.readValue(rs.getString("areas"),
                    mapper.getTypeFactory().constructCollectionType(List.class, String.class))); }
                catch (JsonProcessingException e) { throw new IllegalStateException(e); }
            }, userId, sessionId);
    }

    public Feedback saveFeedback(UUID userId, UUID sessionId, String auth, Feedback input) {
        JsonNode session = sources.workout("/api/v1/workout-sessions/" + sessionId, auth);
        if (!Set.of("COMPLETED", "ABANDONED").contains(session.path("status").asText()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workout is not finished");
        if (input == null || input.difficulty() == null || !Set.of("EASY", "MODERATE", "HARD").contains(input.difficulty())
            || input.discomfortAreas() == null || input.discomfortAreas().size() > 10
            || input.discomfortAreas().stream().anyMatch(a -> a == null || !a.matches("[A-Z_]{1,40}"))
            || new HashSet<>(input.discomfortAreas()).size() != input.discomfortAreas().size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid feedback");
        try {
            int changed = jdbc.update("""
                INSERT INTO workout_feedback(session_id,user_id,difficulty,discomfort_areas)
                VALUES (?,?,?,?::jsonb)
                ON CONFLICT (session_id) DO UPDATE SET difficulty=EXCLUDED.difficulty,
                    discomfort_areas=EXCLUDED.discomfort_areas,updated_at=now()
                WHERE workout_feedback.user_id=EXCLUDED.user_id
                """, sessionId, userId, input.difficulty(), mapper.writeValueAsString(input.discomfortAreas()));
            if (changed != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout not found");
            return input;
        } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }

    private static int plannedSets(JsonNode snapshot) {
        int total = 0; for (JsonNode exercise : snapshot.path("exercises")) total += exercise.path("sets").size(); return total;
    }
    private static boolean performed(JsonNode set) {
        return set.path("actualReps").asInt(0) > 0 || set.path("actualDurationSeconds").asInt(0) > 0;
    }
    private static int performedSets(JsonNode sets) {
        int total = 0; for (JsonNode set : sets) if (performed(set)) total++; return total;
    }
}
