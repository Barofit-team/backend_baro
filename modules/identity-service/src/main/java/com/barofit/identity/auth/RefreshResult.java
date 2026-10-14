package com.barofit.identity.auth;

import com.barofit.identity.token.IssuedTokens;

/**
 * refresh 결과. 실패도 예외가 아닌 값으로 돌려준다.
 * 같은 트랜잭션에서 예외를 던지면 재사용 탐지로 폐기한 세션까지 롤백되기 때문이다. 예외 변환은 커밋 후 컨트롤러가 한다.
 */
public sealed interface RefreshResult {

	record Success(IssuedTokens tokens) implements RefreshResult {
	}

	record Failure(AuthErrorCode errorCode) implements RefreshResult {
	}
}
