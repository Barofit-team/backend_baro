package com.barofit.identity.api;

/** 바로핏 JWT의 iss·aud와 claim 이름 */
public final class BarofitTokenClaims {

	public static final String ISSUER = "barofit-identity";
	public static final String AUDIENCE = "barofit-api";
	public static final String ROLE = "role";

	private BarofitTokenClaims() {
	}
}
