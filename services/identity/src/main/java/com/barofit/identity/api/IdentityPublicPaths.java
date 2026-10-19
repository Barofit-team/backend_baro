package com.barofit.identity.api;

/** 인증 없이 여는 identity 경로 */
public final class IdentityPublicPaths {

	public static final String AUTH = "/api/v1/auth/**";
	public static final String JWKS = "/.well-known/jwks.json";

	public static final String[] ALL = {AUTH, JWKS};

	private IdentityPublicPaths() {
	}
}
