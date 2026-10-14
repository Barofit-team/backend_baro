package com.barofit.identity.auth;

import com.barofit.identity.token.IssuedTokens;

/** refresh 결과. 세션 폐기가 롤백되지 않도록 실패도 값으로 돌려준다 */
public sealed interface RefreshResult {

	record Success(IssuedTokens tokens) implements RefreshResult {
	}

	record Failure(AuthErrorCode errorCode) implements RefreshResult {
	}
}
