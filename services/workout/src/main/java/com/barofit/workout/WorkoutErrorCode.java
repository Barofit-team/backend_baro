package com.barofit.workout;

import com.barofit.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum WorkoutErrorCode implements ErrorCode {
    INVALID_WORKOUT(HttpStatus.BAD_REQUEST, "운동 세션 요청이 올바르지 않습니다."),
    WORKOUT_NOT_FOUND(HttpStatus.NOT_FOUND, "운동 세션을 찾을 수 없습니다."),
    WORKOUT_CONFLICT(HttpStatus.CONFLICT, "운동 세션 상태가 충돌합니다."),
    ROUTINE_NOT_FOUND(HttpStatus.NOT_FOUND, "운동할 루틴을 찾을 수 없습니다."),
    ROUTINE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "루틴 서비스에 연결할 수 없습니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");

    private final HttpStatus status;
    private final String message;
    WorkoutErrorCode(HttpStatus status, String message) { this.status = status; this.message = message; }
    @Override public String code() { return name(); }
    @Override public HttpStatus status() { return status; }
    @Override public String message() { return message; }
}
