package com.barofit.identity.token;

import java.time.Instant;

/**
 * @param refreshToken 원문. 응답으로 한 번만 내보내고 서버에는 해시만 남는다
 */
public record IssuedTokens(String accessToken, Instant accessTokenExpiresAt, String refreshToken) {
}
