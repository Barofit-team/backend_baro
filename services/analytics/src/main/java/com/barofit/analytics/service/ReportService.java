package com.barofit.analytics.service;

import com.barofit.analytics.repository.ReportRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReportService {

    private static final Set<String> EXERCISES = Set.of(
        "pushup", "plank", "bird_dog",
        "leg_raise", "glute_bridge"
    );

    private final ReportRepository repository;
    private final ObjectMapper mapper;

    public ReportService(
            ReportRepository repository,
            ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(
            HttpStatus.BAD_REQUEST, message);
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw bad("Invalid or missing " + field);
        }
        return value.asText();
    }

    private Double optionalNumber(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isNumber() || !Double.isFinite(value.asDouble())
                || value.asDouble() < 0) {
            throw bad("Invalid " + field);
        }
        return value.asDouble();
    }

    private Integer optionalCount(JsonNode node) {
        JsonNode value = node.path("count");
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()
                || value.asInt() < 0) {
            throw bad("Invalid count");
        }
        return value.asInt();
    }

    private double requiredNumber(JsonNode node, String field) {
        Double value = optionalNumber(node, field);
        if (value == null) {
            throw bad("Missing " + field);
        }
        return value;
    }

    @Transactional
    public Map<String, Object> saveSet(
            UUID userId, UUID sessionId,
            UUID setId, JsonNode payload) {

        if (payload == null || !payload.isObject()) {
            throw bad("Invalid payload");
        }

        String version = requiredText(payload, "schemaVersion");
        String type = requiredText(payload, "type");
        String exercise = requiredText(payload, "exercise");
        String engineVersion = requiredText(payload, "engineVersion");

        if (!"2.0".equals(version)
                || !"set.completed".equals(type)
                || !EXERCISES.contains(exercise)) {
            throw bad("Unsupported Pose contract");
        }

        JsonNode summary = payload.path("summary");
        if (!summary.isObject()) {
            throw bad("Missing summary");
        }

        if (!version.equals(requiredText(summary, "schemaVersion"))
                || !exercise.equals(requiredText(summary, "exercise"))
                || !engineVersion.equals(
                    requiredText(summary, "engineVersion"))) {
            throw bad("Summary contract mismatch");
        }

        Integer count = optionalCount(summary);
        Double hold = optionalNumber(summary, "holdSeconds");
        Double observed = optionalNumber(summary, "observedSeconds");
        Double gap = optionalNumber(summary, "gapSeconds");
        Double timestamp = optionalNumber(payload, "timestampMs");

        JsonNode items = summary.path("items");
        if (!items.isObject()) {
            throw bad("Invalid posture items");
        }

        if (payload.toString().length() > 1000000) {
            throw bad("Payload too large");
        }

        Map<String, Object> existing = repository.findSet(setId);
        if (existing != null) {
            if (!existing.get("userId").equals(userId)
                    || !existing.get("sessionId").equals(sessionId)) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Set ID already used");
            }
            try {
                JsonNode oldPayload = mapper.readTree(
                    (String) existing.get("rawResult"));
                if (!oldPayload.equals(payload)) {
                    throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Set ID reused with different payload");
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException(
                    "Stored JSON is invalid", ex);
            }
            return Map.of(
                "setId", setId,
                "sessionId", sessionId,
                "status", "already_saved"
            );
        }

        try {
            repository.insertSet(
                userId, sessionId, setId, exercise,
                engineVersion, timestamp, count,
                hold, observed, gap, payload.toString());

            Iterator<Map.Entry<String, JsonNode>> iterator =
                items.fields();

            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry =
                    iterator.next();

                String itemId = entry.getKey();
                JsonNode item = entry.getValue();

                String basis = requiredText(
                    item, "aggregationBasis");
                String unit = requiredText(item, "unit");

                boolean validBasis =
                    ("repetition".equals(basis)
                        && "repetitions".equals(unit))
                    || ("time".equals(basis)
                        && "seconds".equals(unit));

                if (!validBasis) {
                    throw bad("Invalid item aggregation");
                }

                double compliant =
                    requiredNumber(item, "compliant");
                double warning =
                    requiredNumber(item, "warning");
                double unknown =
                    requiredNumber(item, "unknown");
                double notApplicable =
                    requiredNumber(item, "notApplicable");
                double evaluated =
                    requiredNumber(item, "evaluated");

                if (Math.abs(
                    evaluated - compliant - warning) > 0.000001) {
                    throw bad("Invalid evaluated total");
                }

                Double percentage =
                    optionalNumber(item, "compliancePct");

                if (percentage != null && percentage > 100) {
                    throw bad("Invalid compliance percentage");
                }

                Double expected = evaluated == 0
                    ? null : 100.0 * compliant / evaluated;

                if ((expected == null && percentage != null)
                    || (expected != null
                        && (percentage == null
                            || Math.abs(expected - percentage) > 0.1))) {
                    throw bad("Compliance percentage mismatch");
                }

                repository.insertItem(
                    setId, itemId, basis, unit,
                    compliant, warning, unknown,
                    notApplicable, evaluated, percentage);
            }
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Concurrent duplicate set submission", ex);
        }

        return Map.of(
            "setId", setId,
            "sessionId", sessionId,
            "status", "saved"
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSessionSets(
            UUID userId, UUID sessionId) {
        return repository.findSessionSets(userId, sessionId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSessionSummary(
            UUID userId, UUID sessionId) {

        return repository.findSessionSummary(userId, sessionId);
    }
    @Transactional(readOnly = true)
    public Map<String, Object> getDailySummary(
            UUID userId, java.time.LocalDate date) {

        java.time.Instant from =
            date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

        java.time.Instant until =
            date.plusDays(1)
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant();

        return repository.findPeriodSummary(userId, from, until);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getWeeklySummary(
            UUID userId, java.time.LocalDate startDate) {

        java.time.Instant from =
            startDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();

        java.time.Instant until =
            startDate.plusDays(7)
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant();

        return repository.findPeriodSummary(userId, from, until);
    }
}
