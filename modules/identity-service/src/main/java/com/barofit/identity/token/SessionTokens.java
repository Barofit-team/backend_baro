package com.barofit.identity.token;

import com.barofit.identity.user.User;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 세션(session_id) 단위의 토큰 발급. 호출하는 쪽의 트랜잭션 안에서만 실행한다.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class SessionTokens {

	private final RefreshTokenRepository refreshTokenRepository;
	private final AccessTokenIssuer accessTokenIssuer;

	/** 새 세션을 만든다. 기기마다 로그인할 때마다 새 세션이 생긴다(동시 로그인 허용) */
	public IssuedTokens startSession(User user, Instant now) {
		Instant sessionExpiresAt = now.plus(TokenPolicy.SESSION_TTL);
		return issue(user, UUID.randomUUID(), sessionExpiresAt, now);
	}

	/** 같은 세션에서 토큰을 회전한다. 절대 만료는 이전 행의 값을 그대로 이어받는다 */
	public IssuedTokens rotate(User user, RefreshToken previous, Instant now) {
		return issue(user, previous.getSessionId(), previous.getSessionExpiresAt(), now);
	}

	private IssuedTokens issue(User user, UUID sessionId, Instant sessionExpiresAt, Instant now) {
		String refreshToken = RefreshTokenValues.generate();
		Instant slidingExpiresAt = now.plus(TokenPolicy.REFRESH_TOKEN_TTL);
		Instant expiresAt = slidingExpiresAt.isBefore(sessionExpiresAt) ? slidingExpiresAt : sessionExpiresAt;
		refreshTokenRepository.save(new RefreshToken(user.getId(), sessionId, RefreshTokenValues.hash(refreshToken),
				expiresAt, sessionExpiresAt, now));
		AccessTokenIssuer.IssuedAccessToken accessToken = accessTokenIssuer.issue(user.getId(), user.getRole());
		return new IssuedTokens(accessToken.value(), accessToken.expiresAt(), refreshToken);
	}
}
