package com.barofit.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

	INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String message;

	@Override
	public String code() {
		return name();
	}

	/** Spring MVC 표준 예외 상태를 공통 코드로 매핑 */
	public static CommonErrorCode fromStatus(HttpStatusCode status) {
		return switch (status.value()) {
			case 404 -> NOT_FOUND;
			case 405 -> METHOD_NOT_ALLOWED;
			default -> status.is5xxServerError() ? INTERNAL_SERVER_ERROR : INVALID_INPUT;
		};
	}
}
