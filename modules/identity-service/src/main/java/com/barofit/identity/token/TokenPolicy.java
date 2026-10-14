package com.barofit.identity.token;

import java.time.Duration;

/** 토큰 수명 */
public final class TokenPolicy {

	public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
	/** 슬라이딩 만료. 회전마다 갱신 */
	public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);
	/** 절대 만료 */
	public static final Duration SESSION_TTL = Duration.ofDays(90);

	private TokenPolicy() {
	}
}
