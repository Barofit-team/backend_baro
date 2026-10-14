package com.barofit.identity.api;

/**
 * 바로핏 Access Token(JWT)의 발급자·대상과 claim 이름. 발급(identity)과 검증(app)이 같은 값을 쓴다.
 */
public final class BarofitTokenClaims {

	public static final String ISSUER = "barofit-identity";
	public static final String AUDIENCE = "barofit-api";
	public static final String ROLE = "role";

	private BarofitTokenClaims() {
	}
}
