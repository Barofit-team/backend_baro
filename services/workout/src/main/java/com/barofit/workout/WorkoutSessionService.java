package com.barofit.workout;

import com.barofit.workout.WorkoutDtos.*;
import com.barofit.common.error.BusinessException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkoutSessionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RoutineSnapshotClient routines;

    public WorkoutSessionService(JdbcTemplate jdbc, ObjectMapper json, RoutineSnapshotClient routines) {
        this.jdbc = jdbc;
        this.json = json;
        this.routines = routines;
    }

    @Transactional
    public SessionResponse sync(UUID userId, UUID sessionId, SyncRequest request, String accessToken) {
        validate(request);
        String snapshot = stringify(request.routineSnapshot());
        String hash = sha256(stringify(request));
        int inserted = jdbc.update("""
            INSERT INTO workout.workout_session(id,user_id,routine_id,routine_snapshot,status,revision,payload_hash,
                started_at,ended_at,active_duration_seconds,rest_duration_seconds)
            VALUES (?,?,?,?::jsonb,?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING
            """, sessionId, userId, request.routineId(), snapshot, request.status().name(), request.revision(),
            hash, timestamp(request.startedAt()), timestamp(request.endedAt()), request.activeDurationSeconds(), request.restDurationSeconds());

        List<Stored> rows = jdbc.query("""
            SELECT user_id,routine_id,routine_snapshot,revision,payload_hash,status,started_at
            FROM workout.workout_session WHERE id=? FOR UPDATE
            """, (rs, n) -> new Stored((UUID) rs.getObject("user_id"), (UUID) rs.getObject("routine_id"),
            parse(rs.getString("routine_snapshot")),
            rs.getLong("revision"), rs.getString("payload_hash"), SessionStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("started_at").toInstant()), sessionId);
        Stored stored = rows.getFirst();
        if (!stored.userId().equals(userId)) throw notFound();
        if (!stored.routineId().equals(request.routineId()) || !stored.startedAt().equals(request.startedAt()))
            throw conflict("세션의 원본 루틴과 시작 시각은 변경할 수 없습니다.");
        if (!stored.snapshot().equals(request.routineSnapshot()))
            throw conflict("시작 시점의 루틴 스냅샷은 변경할 수 없습니다.");
        if (inserted == 1) {
            JsonNode source = routines.fetch(request.routineId(), request.routineSnapshot().path("type").asText(),
                accessToken);
            if (!sameJsonValue(source, request.routineSnapshot(), true))
                throw conflict("루틴 원본이 변경되었습니다. 최신 루틴을 다시 조회해 주세요.");
            replaceSets(sessionId, request.sets());
            enqueueCompletionIfQualified(sessionId, request);
            return get(userId, sessionId);
        }
        if (stored.revision() == request.revision()) {
            if (!stored.hash().equals(hash)) throw conflict("동일 revision에 다른 내용이 전송되었습니다.");
            return get(userId, sessionId);
        }
        if (stored.revision() > request.revision()) throw conflict("오래된 세션 상태입니다.");
        if (stored.status() == SessionStatus.COMPLETED || stored.status() == SessionStatus.ABANDONED)
            throw conflict("종료된 세션은 수정할 수 없습니다.");

        jdbc.update("""
            UPDATE workout.workout_session SET status=?,revision=?,payload_hash=?,
                ended_at=?,active_duration_seconds=?,rest_duration_seconds=?,updated_at=now() WHERE id=?
            """, request.status().name(), request.revision(), hash,
            timestamp(request.endedAt()), request.activeDurationSeconds(), request.restDurationSeconds(), sessionId);
        replaceSets(sessionId, request.sets());
        enqueueCompletionIfQualified(sessionId, request);
        return get(userId, sessionId);
    }

    private void enqueueCompletionIfQualified(UUID sessionId, SyncRequest request) {
        if ((request.status() == SessionStatus.COMPLETED || request.status() == SessionStatus.ABANDONED)
            && WorkoutCompletionPolicy.qualifies(request.routineSnapshot(), request.sets())) {
            jdbc.update("""
                INSERT INTO workout.analytics_completion_outbox(session_id)
                VALUES (?) ON CONFLICT (session_id) DO NOTHING
                """, sessionId);
        }
    }

    private void replaceSets(UUID sessionId, List<SetResult> sets) {
        jdbc.update("DELETE FROM workout.workout_set_result WHERE session_id=?", sessionId);
        for (SetResult set : sets) {
            jdbc.update("""
                INSERT INTO workout.workout_set_result(session_id,routine_set_id,actual_reps,actual_duration_seconds,
                    feedback_codes,error_counts,feedback_text,model_version,reference_model_id,tracking_available_pct,completed_at)
                VALUES (?,?,?,?,?::jsonb,?::jsonb,?,?,?,?,?)
                """, sessionId, set.routineSetId(), set.actualReps(), set.actualDurationSeconds(),
                stringify(set.feedbackCodes()), stringify(set.errorCounts()), set.feedbackText(), set.modelVersion(),
                set.referenceModelId(), set.trackingAvailablePct(), timestamp(set.completedAt()));
        }
    }

    @Transactional(readOnly = true)
    public SessionResponse get(UUID userId, UUID sessionId) {
        List<SessionResponse> sessions = jdbc.query("""
            SELECT id,routine_id,routine_snapshot,status,revision,started_at,ended_at,active_duration_seconds,rest_duration_seconds
            FROM workout.workout_session WHERE id=? AND user_id=?
            """, (rs, n) -> new SessionResponse((UUID) rs.getObject("id"), (UUID) rs.getObject("routine_id"),
            parse(rs.getString("routine_snapshot")), SessionStatus.valueOf(rs.getString("status")),
            rs.getLong("revision"), rs.getTimestamp("started_at").toInstant(),
            instant(rs, "ended_at"), rs.getInt("active_duration_seconds"), List.of(), (Integer) rs.getObject("rest_duration_seconds")), sessionId, userId);
        if (sessions.isEmpty()) throw notFound();
        SessionResponse s = sessions.getFirst();
        List<SetResult> sets = jdbc.query("""
            SELECT routine_set_id,actual_reps,actual_duration_seconds,feedback_codes,error_counts,feedback_text,
                model_version,reference_model_id,tracking_available_pct,completed_at
            FROM workout.workout_set_result WHERE session_id=? ORDER BY completed_at,routine_set_id
            """, (rs, n) -> new SetResult((UUID) rs.getObject("routine_set_id"),
            (Integer) rs.getObject("actual_reps"), (Integer) rs.getObject("actual_duration_seconds"),
            json.convertValue(parse(rs.getString("feedback_codes")), json.getTypeFactory().constructCollectionType(List.class, String.class)),
            json.convertValue(parse(rs.getString("error_counts")), json.getTypeFactory().constructMapType(java.util.Map.class, String.class, Integer.class)),
            rs.getString("feedback_text"), rs.getString("model_version"), rs.getString("reference_model_id"),
            (Double) rs.getObject("tracking_available_pct"), rs.getTimestamp("completed_at").toInstant()),
            sessionId);
        return new SessionResponse(s.id(), s.routineId(), s.routineSnapshot(), s.status(), s.revision(),
            s.startedAt(), s.endedAt(), s.activeDurationSeconds(), sets, s.restDurationSeconds());
    }

    @Transactional(readOnly = true)
    public List<SessionSummary> list(UUID userId) {
        return jdbc.query("""
            SELECT id,routine_id,status,revision,started_at,ended_at,active_duration_seconds
            FROM workout.workout_session WHERE user_id=? ORDER BY started_at DESC LIMIT 100
            """, (rs, n) -> new SessionSummary((UUID) rs.getObject("id"), (UUID) rs.getObject("routine_id"),
            SessionStatus.valueOf(rs.getString("status")), rs.getLong("revision"),
            rs.getTimestamp("started_at").toInstant(), instant(rs,"ended_at"),
            rs.getInt("active_duration_seconds")), userId);
    }

    public record HistoryItem(SessionResponse session, boolean completed) { }

    @Transactional(readOnly = true)
    public List<HistoryItem> history(UUID userId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)
            || ChronoUnit.DAYS.between(startDate, endDate) > 30)
            throw bad("운동 기록 조회 기간은 최대 31일입니다.");
        ZoneId zone = ZoneId.of("Asia/Seoul");
        var ids = jdbc.query("""
            SELECT id FROM workout.workout_session
            WHERE user_id=? AND status IN ('COMPLETED','ABANDONED') AND ended_at>=? AND ended_at<?
            ORDER BY ended_at DESC,id DESC
            """, (rs, row) -> (UUID) rs.getObject("id"), userId,
            Timestamp.from(startDate.atStartOfDay(zone).toInstant()),
            Timestamp.from(endDate.plusDays(1).atStartOfDay(zone).toInstant()));
        return ids.stream().map(id -> {
            SessionResponse session = get(userId, id);
            return new HistoryItem(session, WorkoutCompletionPolicy.qualifies(session.routineSnapshot(), session.sets()));
        }).toList();
    }

    private void validate(SyncRequest r) {
        if (r == null || r.routineId() == null || r.status() == null || r.revision() == null
            || r.revision() < 0 || r.startedAt() == null || r.activeDurationSeconds() == null
            || r.activeDurationSeconds() < 0 || (r.restDurationSeconds() != null && r.restDurationSeconds() < 0) || r.sets() == null || r.sets().size() > 100)
            throw bad("필수 세션 값 또는 세트 목록이 올바르지 않습니다.");
        boolean terminal = r.status() == SessionStatus.COMPLETED || r.status() == SessionStatus.ABANDONED;
        if (terminal != (r.endedAt() != null) ||
            (r.endedAt() != null && r.endedAt().isBefore(r.startedAt())))
            throw bad("종료 상태와 종료 시각이 맞지 않습니다.");
        if (r.endedAt() != null && r.restDurationSeconds() != null
            && r.restDurationSeconds() > java.time.Duration.between(r.startedAt(), r.endedAt()).getSeconds())
            throw bad("휴식 시간은 전체 경과 시간을 초과할 수 없습니다.");
        JsonNode snapshot = r.routineSnapshot();
        if (snapshot == null || !snapshot.isObject() || !r.routineId().toString().equals(snapshot.path("id").asText())
            || !("USER".equals(snapshot.path("type").asText())
                || "TEMPLATE".equals(snapshot.path("type").asText()))
            || !snapshot.path("exercises").isArray() || snapshot.path("exercises").isEmpty()
            || stringify(snapshot).length() > 65536)
            throw bad("시작 시점의 루틴 상세 스냅샷이 필요합니다.");
        Set<UUID> allowed = new HashSet<>();
        for (JsonNode exercise : snapshot.path("exercises")) {
            if (!exercise.path("sets").isArray()) throw bad("루틴 세트 정보가 없습니다.");
            for (JsonNode set : exercise.path("sets")) {
                try { allowed.add(UUID.fromString(set.path("id").asText())); }
                catch (IllegalArgumentException ex) { throw bad("루틴 세트 ID가 올바르지 않습니다."); }
            }
        }
        Set<UUID> seen = new HashSet<>();
        for (SetResult set : r.sets()) {
            if (set == null || set.routineSetId() == null || !allowed.contains(set.routineSetId())
                || !seen.add(set.routineSetId()) || set.completedAt() == null
                || (set.actualReps() == null && set.actualDurationSeconds() == null)
                || (set.actualReps() != null && (set.actualReps() < 0 || set.actualReps() > 10000))
                || (set.actualDurationSeconds() != null && set.actualDurationSeconds() < 0)
                || set.feedbackCodes() == null || set.feedbackCodes().size() > 20
                || set.feedbackCodes().stream().anyMatch(code -> code == null || !code.matches("[A-Z_]{1,50}"))
                || set.errorCounts() == null || set.errorCounts().size() > 20
                || set.errorCounts().entrySet().stream().anyMatch(entry -> entry.getKey() == null
                    || !entry.getKey().matches("[A-Z_]{1,50}") || entry.getValue() == null || entry.getValue() < 0)
                || (set.feedbackText() != null && set.feedbackText().length() > 1000)
                || (set.modelVersion() != null && set.modelVersion().length() > 100)
                || (set.referenceModelId() != null && set.referenceModelId().length() > 128)
                || (set.trackingAvailablePct() != null && (!Double.isFinite(set.trackingAvailablePct())
                    || set.trackingAvailablePct() < 0 || set.trackingAvailablePct() > 100)))
                throw bad("세트 결과가 올바르지 않습니다.");
        }
    }

    private String stringify(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JacksonException ex) { throw bad("JSON을 처리할 수 없습니다."); }
    }
    private static boolean sameJsonValue(JsonNode left, JsonNode right, boolean root) {
        if (left.isNumber() && right.isNumber())
            return left.decimalValue().compareTo(right.decimalValue()) == 0;
        if (left.isObject() && right.isObject()) {
            for (var entry : left.properties()) {
                String name = entry.getKey();
                if (root && (name.equals("createdAt") || name.equals("updatedAt"))) continue;
                if (!right.has(name) || !sameJsonValue(entry.getValue(), right.get(name), false))
                    return false;
            }
            for (var entry : right.properties()) {
                String name = entry.getKey();
                if (root && (name.equals("createdAt") || name.equals("updatedAt"))) continue;
                if (!left.has(name)) return false;
            }
            return true;
        }
        if (left.isArray() && right.isArray()) {
            if (left.size() != right.size()) return false;
            for (int i = 0; i < left.size(); i++)
                if (!sameJsonValue(left.get(i), right.get(i), false)) return false;
            return true;
        }
        return left.equals(right);
    }
    private JsonNode parse(String value) {
        try { return json.readTree(value); }
        catch (JacksonException ex) { throw new IllegalStateException("저장된 JSON이 손상되었습니다.", ex); }
    }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static BusinessException bad(String detail) { return new BusinessException(WorkoutErrorCode.INVALID_WORKOUT, detail); }
    private static BusinessException conflict(String detail) { return new BusinessException(WorkoutErrorCode.WORKOUT_CONFLICT, detail); }
    private static BusinessException notFound() { return new BusinessException(WorkoutErrorCode.WORKOUT_NOT_FOUND); }
    private record Stored(UUID userId, UUID routineId, JsonNode snapshot, long revision,
                          String hash, SessionStatus status, Instant startedAt) {}
}
