package com.barofit.identity.auth;

import com.barofit.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

	/** 소셜 ID Token 서명·만료·iss·aud 검증 실패. 앱 동작: SDK 로그인부터 다시 */
	AUTH_INVALID_ID_TOKEN(HttpStatus.UNAUTHORIZED, "소셜 로그인 정보를 확인할 수 없습니다."),
	/** 소셜 공개키(JWKS)를 가져오지 못함. 앱 동작: 잠시 후 재시도 */
	AUTH_SOCIAL_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "소셜 로그인 서비스에 일시적으로 연결할 수 없습니다."),
	/** 세션 폐기(재사용 탐지 등). 앱 동작: 재로그인 */
	AUTH_SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "로그인 세션이 만료되었습니다. 다시 로그인해 주세요."),
	/** Refresh Token 만료·없음. 앱 동작: 재로그인 */
	AUTH_INVALID_REFRESH(HttpStatus.UNAUTHORIZED, "로그인 세션이 만료되었습니다. 다시 로그인해 주세요."),
	/** 탈퇴한 사용자. 앱 동작: 로컬 토큰 삭제 후 로그인 화면 */
	AUTH_USER_WITHDRAWN(HttpStatus.UNAUTHORIZED, "탈퇴한 사용자입니다.");

	private final HttpStatus status;
	private final String message;

	@Override
	public String code() {
		return name();
	}
}
