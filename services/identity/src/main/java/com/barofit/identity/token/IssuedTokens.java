package com.barofit.identity.token;

import java.time.Instant;

/** refreshToken은 원문. 서버에는 해시만 저장 */
public record IssuedTokens(String accessToken, Instant accessTokenExpiresAt, String refreshToken) {
}
