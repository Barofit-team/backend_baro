package com.barofit.workout;

import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorkoutDtos {
    private WorkoutDtos() {}

    @Schema(description = "기기가 생성한 UUID를 경로에 사용합니다. 오프라인에서 운동한 뒤에도 같은 요청을 재전송할 수 있습니다.")
    public record SyncRequest(UUID routineId, JsonNode routineSnapshot, SessionStatus status,
                              Long revision, Instant startedAt, Instant endedAt,
                              Integer activeDurationSeconds, List<SetResult> sets,
                              @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                              Integer restDurationSeconds) {
        public SyncRequest(UUID routineId, JsonNode routineSnapshot, SessionStatus status, Long revision,
                           Instant startedAt, Instant endedAt, Integer activeDurationSeconds, List<SetResult> sets) {
            this(routineId, routineSnapshot, status, revision, startedAt, endedAt, activeDurationSeconds, sets, null);
        }
    }

    @Schema(description = "카메라 영상·관절 프레임은 전송하지 않고 세트 요약만 저장합니다.")
    public record SetResult(UUID routineSetId, Integer actualReps, Integer actualDurationSeconds,
                            List<String> feedbackCodes, Map<String, Integer> errorCounts,
                            String feedbackText, String modelVersion, String referenceModelId,
                            Double trackingAvailablePct,
                            Instant completedAt) {}

    public record SessionResponse(UUID id, UUID routineId, JsonNode routineSnapshot, SessionStatus status,
                                  long revision, Instant startedAt, Instant endedAt,
                                  int activeDurationSeconds, List<SetResult> sets, Integer restDurationSeconds) {}

    public record SessionSummary(UUID id, UUID routineId, SessionStatus status, long revision,
                                 Instant startedAt, Instant endedAt, int activeDurationSeconds) {}
}
