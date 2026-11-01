package com.barofit.report.controller;

import com.barofit.report.service.ReportViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "운동 리포트 화면", description = "월간 달력, 운동 결과 및 사용자 피드백")
@SecurityRequirement(name = "bearerAuth")
public class ReportViewController {
    private final ReportViewService service;
    @org.springframework.beans.factory.annotation.Autowired
    private com.barofit.report.service.GeneratedReportService generated;
    public ReportViewController(ReportViewService service) { this.service = service; }

    private UUID user(Principal principal) {
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        try { return UUID.fromString(principal.getName()); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user"); }
    }

    @GetMapping("/calendar")
    @Operation(summary = "월간 운동 달력", description = "한국 시간 기준 완료·부분 실행·미실행·계획·빈 날짜 및 현재 연속 운동 일수를 반환합니다.")
    public ReportViewService.Month calendar(@RequestParam int year, @RequestParam int month,
                                            Principal principal, HttpServletRequest request) {
        user(principal);
        return service.calendar(year, month, request.getHeader("Authorization"));
    }

    @GetMapping("/history")
    @Operation(summary = "월간 운동 일지", description = "선택한 월의 운동 및 미실행 계획을 최신 날짜순으로 페이징합니다.")
    public ReportViewService.RecordPage history(@RequestParam int year, @RequestParam int month,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
        Principal principal, HttpServletRequest request) {
        user(principal);
        return service.records(year, month, page, size, request.getHeader("Authorization"));
    }

    @GetMapping("/dates/{date}")
    @Operation(summary = "날짜별 운동 일지", description = "선택한 날짜의 운동 기록 또는 미실행 계획을 조회합니다.")
    public java.util.List<ReportViewService.RecordCard> date(@PathVariable
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        Principal principal, HttpServletRequest request) {
        user(principal);
        return service.dateRecords(date, request.getHeader("Authorization"));
    }

    @GetMapping("/sessions/{sessionId}/result")
    @Operation(summary = "운동 결과 상세", description = "시작 시점 루틴과 세트 수행 결과, 자세 리포트, 사용자가 남긴 피드백을 반환합니다.")
    public ReportViewService.WorkoutResult result(@PathVariable UUID sessionId, Principal principal,
                                                   HttpServletRequest request) {
        return service.result(user(principal), sessionId, request.getHeader("Authorization"));
    }

    @PostMapping("/sessions/{sessionId}/generated")
    @Operation(summary = "종료 운동의 리포트 생성", description = "수행 세트의 AI 결과가 모두 동기화된 후 전체·종목별 리포트를 한 번 생성하고 저장합니다. 재요청 시 저장된 리포트를 반환합니다.")
    public com.barofit.report.service.GeneratedReportService.Generated generate(@PathVariable UUID sessionId,
        Principal principal, HttpServletRequest request) {
        return generated.generate(user(principal), sessionId, request.getHeader("Authorization"));
    }

    @GetMapping("/sessions/{sessionId}/generated")
    @Operation(summary = "저장된 운동 리포트 조회")
    public com.barofit.report.service.GeneratedReportService.Generated generated(@PathVariable UUID sessionId,
        Principal principal, HttpServletRequest request) {
        return generated.get(user(principal), sessionId, request.getHeader("Authorization"));
    }

    @PutMapping("/sessions/{sessionId}/feedback")
    @Operation(summary = "운동 후 피드백 저장·수정", description = "difficulty는 EASY, MODERATE, HARD, discomfortAreas는 부위 코드 배열입니다. 불편 부위가 없으면 빈 배열을 보냅니다.")
    public ReportViewService.Feedback feedback(@PathVariable UUID sessionId,
        @RequestBody ReportViewService.Feedback feedback, Principal principal, HttpServletRequest request) {
        return service.saveFeedback(user(principal), sessionId, request.getHeader("Authorization"), feedback);
    }

    @GetMapping("/exercises/{exerciseId}/scores")
    @Operation(summary = "종목 자세 점수 추이", description = "본인의 이전 운동 점수를 최신순으로 반환합니다.")
    public java.util.List<ReportViewService.ScoreHistory> scores(@PathVariable String exerciseId,
        @RequestParam(defaultValue = "3") int limit, Principal principal, HttpServletRequest request) {
        return service.scoreHistory(user(principal), exerciseId, limit, request.getHeader("Authorization"));
    }
}
