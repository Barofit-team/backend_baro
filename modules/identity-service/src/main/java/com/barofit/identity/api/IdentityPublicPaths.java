package com.barofit.identity.api;

/**
 * identity 모듈이 인증 없이 노출하는 경로. app의 보안 설정은 경로를 하드코딩하지 않고 이 상수를 참조한다.
 */
public final class IdentityPublicPaths {

	public static final String AUTH = "/api/v1/auth/**";
	public static final String JWKS = "/.well-known/jwks.json";

	public static final String[] ALL = {AUTH, JWKS};

	private IdentityPublicPaths() {
	}
}
