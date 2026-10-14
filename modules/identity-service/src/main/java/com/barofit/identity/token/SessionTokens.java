package com.barofit.identity.token;

import com.barofit.identity.user.User;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 세션 단위 토큰 발급. 호출하는 쪽 트랜잭션 안에서 실행 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class SessionTokens {

	private final RefreshTokenRepository refreshTokenRepository;
	private final AccessTokenIssuer accessTokenIssuer;

	/** 새 세션 생성(동시 로그인 허용) */
	public IssuedTokens startSession(User user, Instant now) {
		Instant sessionExpiresAt = now.plus(TokenPolicy.SESSION_TTL);
		return issue(user, UUID.randomUUID(), sessionExpiresAt, now);
	}

	/** 토큰 회전. 절대 만료는 유지 */
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
