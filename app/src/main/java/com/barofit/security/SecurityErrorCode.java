package com.barofit.security;

import com.barofit.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/**
 * 바로핏 Access Token 검증 실패 코드. 앱은 code에 따라 refresh를 시도하거나 재로그인한다.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum SecurityErrorCode implements ErrorCode {

	/** 앱 동작: refresh 시도 */
	AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Access Token이 만료되었습니다."),
	/** 위조·형식 오류, 또는 보호된 API에 토큰 없이 접근. 앱 동작: 재로그인 */
	AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다.");

	private final HttpStatus status;
	private final String message;

	@Override
	public String code() {
		return name();
	}
}
