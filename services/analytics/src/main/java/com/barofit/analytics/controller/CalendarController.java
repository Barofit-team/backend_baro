
package com.barofit.analytics.controller;

import com.barofit.analytics.dto.CalendarEntryResponse;
import com.barofit.analytics.service.CalendarService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import jakarta.servlet.http.HttpServletRequest;
import java.time.DateTimeException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calendar")
public class CalendarController {

    private final CalendarService service;

    public CalendarController(CalendarService service) {
        this.service = service;
    }

    private UUID currentUserId(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authenticated user"
            );
        }
    }

    // 로그인 사용자의 월별 운동 캘린더 조회
    @GetMapping
    public List<CalendarEntryResponse> getMonthlyCalendar(
            HttpServletRequest request,
            @RequestParam int year,
            @RequestParam int month
    ) {
        UUID userId = currentUserId(request.getUserPrincipal());

        try {
            return service.getMonthlyCalendar(
                    userId,
                    year,
                    month
            );
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid year or month"
            );
        }
    }
}
