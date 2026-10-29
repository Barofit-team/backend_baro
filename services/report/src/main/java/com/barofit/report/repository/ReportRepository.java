package com.barofit.report.repository;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class ReportRepository {

    private final JdbcTemplate jdbc;

    public ReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> findSet(UUID setId) {
        List<Map<String, Object>> rows = jdbc.query(
            """
            SELECT set_id, session_id, user_id,
                   raw_result::text AS raw_result
            FROM exercise_set_results
            WHERE set_id = ?
            """,
            (rs, rowNum) -> Map.<String, Object>of(
                "setId", rs.getObject("set_id", UUID.class),
                "sessionId", rs.getObject("session_id", UUID.class),
                "userId", rs.getObject("user_id", UUID.class),
                "rawResult", rs.getString("raw_result")
            ),
            setId
        );

        return rows.isEmpty() ? null : rows.get(0);
    }

    public void insertSet(
            UUID userId, UUID sessionId, UUID setId,
            String exercise, String engineVersion,
            Double timestampMs, Integer count,
            Double holdSeconds, Double observedSeconds,
            Double gapSeconds, String rawJson) {

        jdbc.update(
            """
            INSERT INTO exercise_set_results (
                set_id, session_id, user_id, exercise,
                schema_version, engine_version,
                frame_timestamp_ms, rep_count,
                hold_seconds, observed_seconds, gap_seconds,
                raw_result
            )
            VALUES (?, ?, ?, ?, '2.0', ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
            """,
            setId, sessionId, userId, exercise,
            engineVersion, timestampMs, count,
            holdSeconds, observedSeconds, gapSeconds, rawJson
        );
    }

    public void insertItem(
            UUID setId, String itemId,
            String basis, String unit,
            double compliant, double warning,
            double unknown, double notApplicable,
            double evaluated, Double compliancePct) {

        jdbc.update(
            """
            INSERT INTO posture_item_results (
                set_id, item_id, aggregation_basis, unit,
                compliant, warning, unknown_count,
                not_applicable, evaluated, compliance_pct
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            setId, itemId, basis, unit,
            compliant, warning, unknown,
            notApplicable, evaluated, compliancePct
        );
    }

    public List<Map<String, Object>> findSessionSets(
            UUID userId, UUID sessionId) {

        return jdbc.query(
            """
            SELECT set_id, exercise, engine_version,
                   rep_count, hold_seconds, observed_seconds,
                   received_at, raw_result::text AS raw_result
            FROM exercise_set_results
            WHERE user_id = ? AND session_id = ?
            ORDER BY received_at, set_id
            """,
            (rs, rowNum) -> Map.<String, Object>of(
                "setId", rs.getObject("set_id", UUID.class),
                "exercise", rs.getString("exercise"),
                "engineVersion", rs.getString("engine_version"),
                "rawResult", rs.getString("raw_result")
            ),
            userId, sessionId
        );
    }

    public Map<String, Object> findSessionSummary(
            UUID userId, UUID sessionId) {

        List<Map<String, Object>> totals = jdbc.query(
            """
            SELECT COUNT(*) AS set_count,
                   COALESCE(SUM(rep_count), 0) AS total_reps,
                   COALESCE(SUM(hold_seconds), 0) AS total_hold_seconds,
                   COALESCE(SUM(observed_seconds), 0) AS total_observed_seconds
            FROM exercise_set_results
            WHERE user_id = ? AND session_id = ?
            """,
            (rs, rowNum) -> Map.<String, Object>of(
                "setCount", rs.getLong("set_count"),
                "totalReps", rs.getLong("total_reps"),
                "totalHoldSeconds", rs.getDouble("total_hold_seconds"),
                "totalObservedSeconds", rs.getDouble("total_observed_seconds")
            ),
            userId, sessionId
        );

        List<Map<String, Object>> postureItems = jdbc.query(
            """
            SELECT p.item_id,
                   p.aggregation_basis,
                   p.unit,
                   s.exercise,
                   s.engine_version,
                   SUM(p.compliant) AS compliant,
                   SUM(p.warning) AS warning,
                   SUM(p.unknown_count) AS unknown_count,
                   SUM(p.not_applicable) AS not_applicable,
                   SUM(p.evaluated) AS evaluated
            FROM posture_item_results p
            JOIN exercise_set_results s ON s.set_id = p.set_id
            WHERE s.user_id = ? AND s.session_id = ?
            GROUP BY p.item_id, p.aggregation_basis,
                     p.unit, s.exercise, s.engine_version
            ORDER BY s.exercise, p.item_id, s.engine_version
            """,
            (rs, rowNum) -> {
                double compliant = rs.getDouble("compliant");
                double warning = rs.getDouble("warning");
                double evaluated = rs.getDouble("evaluated");

                Map<String, Object> item = new java.util.LinkedHashMap<>();

                item.put("itemId", rs.getString("item_id"));
                item.put("exercise", rs.getString("exercise"));
                item.put("engineVersion", rs.getString("engine_version"));
                item.put("aggregationBasis", rs.getString("aggregation_basis"));
                item.put("unit", rs.getString("unit"));
                item.put("compliant", compliant);
                item.put("warning", warning);
                item.put("unknown", rs.getDouble("unknown_count"));
                item.put("notApplicable", rs.getDouble("not_applicable"));
                item.put("evaluated", evaluated);
                item.put(
                    "compliancePct",
                    evaluated > 0 ? 100.0 * compliant / evaluated : null
                );

                return item;
            },
            userId, sessionId
        );

        Map<String, Object> result = new java.util.LinkedHashMap<>();

        result.put("sessionId", sessionId);
        result.putAll(totals.get(0));
        result.put("postureItems", postureItems);

        return result;
    }
    public Map<String, Object> findPeriodSummary(
            UUID userId,
            java.time.Instant from,
            java.time.Instant until) {

        return jdbc.queryForObject(
            """
            SELECT COUNT(*) AS set_count,
                   COALESCE(SUM(rep_count), 0) AS total_reps,
                   COALESCE(SUM(hold_seconds), 0) AS total_hold_seconds,
                   COALESCE(SUM(observed_seconds), 0) AS total_observed_seconds,
                   COUNT(DISTINCT session_id) AS session_count
            FROM exercise_set_results
            WHERE user_id = ?
              AND received_at >= ?
              AND received_at < ?
            """,
            (rs, rowNum) -> Map.<String, Object>of(
                "setCount", rs.getLong("set_count"),
                "sessionCount", rs.getLong("session_count"),
                "totalReps", rs.getLong("total_reps"),
                "totalHoldSeconds", rs.getDouble("total_hold_seconds"),
                "totalObservedSeconds", rs.getDouble("total_observed_seconds")
            ),
            userId,
            java.sql.Timestamp.from(from),
            java.sql.Timestamp.from(until)
        );
    }
}
