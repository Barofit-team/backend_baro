package com.barofit.workout;

import com.barofit.workout.WorkoutDtos.*;
import com.barofit.common.error.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workout-sessions")
@Tag(name = "Workout Session", description = "온디바이스 운동 상태·결과 동기화")
@SecurityRequirement(name = "workoutAuth")
public class WorkoutSessionController {
    private final WorkoutSessionService service;
    private final WeeklyWorkoutProgressService progress;

    public WorkoutSessionController(WorkoutSessionService service, WeeklyWorkoutProgressService progress) {
        this.service = service;
        this.progress = progress;
    }

    @GetMapping("/weekly-progress")
    @Operation(summary = "주간 완료 날짜", description = "종료된 세션에서 모든 예정 세트를 수행한 종목 수가 전체 종목 수의 약 70% 이상이면 완료로 표시합니다. 필요한 종목 수는 가장 가까운 정수로 반올림하므로 3종목 중 2종목이면 완료입니다. Asia/Seoul 기준이며 같은 날짜는 한 번만 셉니다.")
    public WeeklyWorkoutProgressService.Result weeklyProgress(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart, Principal principal) {
        return progress.get(user(principal), weekStart);
    }

    @GetMapping("/streak")
    @Operation(summary = "현재 연속 운동 일수", description = "한국 시간 기준 오늘 또는 어제부터 연속으로 완료한 날짜 수입니다.")
    public WeeklyWorkoutProgressService.StreakResult streak(Principal principal) {
        return progress.currentStreak(user(principal));
    }

    @PutMapping("/{sessionId}")
    @Operation(summary = "운동 세션 생성·동기화", description = "기기가 UUID와 revision을 생성합니다. 첫 저장 시 Routine 서비스의 원본과 소유권을 검증합니다. 앱에서 실제 측정한 휴식 시간을 restDurationSeconds(초, 선택)로 보내며 일시정지 시간은 제외합니다. 전체 세트 결과를 매번 보내며 동일 revision 재전송은 멱등 처리합니다. 오프라인 종료 후 한 번에 업로드할 수 있지만, 첫 업로드 전에 원본 루틴이 변경·삭제되면 거절됩니다.")
    public SessionResponse sync(@PathVariable UUID sessionId, @RequestBody SyncRequest request,
                                Principal principal, @AuthenticationPrincipal Jwt jwt) {
        return service.sync(user(principal), sessionId, request, jwt.getTokenValue());
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "운동 세션 상세·이어하기")
    public SessionResponse get(@PathVariable UUID sessionId, Principal principal) {
        return service.get(user(principal), sessionId);
    }

    @GetMapping("/history")
    @Operation(summary = "기간별 종료 운동 기록", description = "한국 시간 기준 최대 31일의 종료 세션과 루틴 완료 여부를 반환합니다.")
    public List<WorkoutSessionService.HistoryItem> history(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, Principal principal) {
        return service.history(user(principal), startDate, endDate);
    }

    @GetMapping
    @Operation(summary = "최근 운동 세션 100개")
    public List<SessionSummary> list(Principal principal) { return service.list(user(principal)); }

    private static UUID user(Principal principal) {
        if (principal == null) throw new BusinessException(WorkoutErrorCode.AUTHENTICATION_REQUIRED);
        try { return UUID.fromString(principal.getName()); }
        catch (IllegalArgumentException ex) { throw new BusinessException(WorkoutErrorCode.AUTHENTICATION_REQUIRED); }
    }
}
