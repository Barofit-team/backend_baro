package com.barofit.identity.token;

import java.time.Duration;

/**
 * 바로핏 토큰 수명. 설계 문서 4장의 확정 값이다.
 */
public final class TokenPolicy {

	public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
	/** Refresh Token 슬라이딩 만료. 회전할 때마다 다시 계산한다 */
	public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);
	/** 세션 절대 만료. 회전해도 늘어나지 않는다 */
	public static final Duration SESSION_TTL = Duration.ofDays(90);

	private TokenPolicy() {
	}
}
