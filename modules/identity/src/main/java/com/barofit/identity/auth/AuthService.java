package com.barofit.identity.auth;

import com.barofit.identity.social.SocialIdTokenVerifier;
import com.barofit.identity.social.SocialIdentity;
import com.barofit.identity.token.RefreshToken;
import com.barofit.identity.token.RefreshTokenRepository;
import com.barofit.identity.token.RefreshTokenValues;
import com.barofit.identity.token.SessionTokens;
import com.barofit.identity.user.SocialProvider;
import com.barofit.identity.user.User;
import com.barofit.identity.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

	private final SocialIdTokenVerifier socialIdTokenVerifier;
	private final LoginTransactions loginTransactions;
	private final RefreshTokenRepository refreshTokenRepository;
	private final UserRepository userRepository;
	private final SessionTokens sessionTokens;
	private final Clock clock;

	/**
	 * 소셜 로그인과 가입. 트랜잭션을 열지 않는다(ID Token 검증의 네트워크 호출, 동시 가입 재조회 때문).
	 */
	public LoginResult socialLogin(SocialProvider provider, String idToken) {
		SocialIdentity identity = socialIdTokenVerifier.verify(provider, idToken);
		return loginTransactions.loginExisting(identity)
				.orElseGet(() -> registerOrLoginConcurrently(identity));
	}

	// 같은 신규 사용자의 요청이 동시에 들어오면 한쪽은 UNIQUE 위반이 난다.
	// PostgreSQL은 제약 위반 후 트랜잭션을 abort 상태로 만드므로 새 트랜잭션에서 한 번만 다시 조회한다
	private LoginResult registerOrLoginConcurrently(SocialIdentity identity) {
		try {
			return loginTransactions.register(identity);
		} catch (DataIntegrityViolationException e) {
			log.info("Concurrent sign-up detected for {}; retrying as login", identity.provider());
			return loginTransactions.loginExisting(identity)
					.orElseThrow(() -> new IllegalStateException("가입 충돌 후 재조회에 실패했습니다", e));
		}
	}

	/**
	 * Refresh Token 회전. 조건부 UPDATE의 영향 행 수로 판정해, 같은 토큰으로 동시에 들어온 요청 중 하나만 성공한다.
	 */
	@Transactional
	public RefreshResult refresh(String refreshToken) {
		Instant now = clock.instant();
		String tokenHash = RefreshTokenValues.hash(refreshToken);
		if (refreshTokenRepository.revokeIfActive(tokenHash, now) == 1) {
			RefreshToken previous = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
			User user = userRepository.findById(previous.getUserId()).orElseThrow();
			// 탈퇴하면 토큰이 모두 폐기되므로 실제로는 위의 UPDATE에서 걸러진다
			if (!user.isActive()) {
				return new RefreshResult.Failure(AuthErrorCode.AUTH_USER_WITHDRAWN);
			}
			return new RefreshResult.Success(sessionTokens.rotate(user, previous, now));
		}
		Optional<RefreshToken> token = refreshTokenRepository.findByTokenHash(tokenHash);
		if (token.isPresent() && token.get().isRevoked()) {
			// 폐기된 토큰의 재사용: 탈취 가능성이 있으므로 세션 전체를 폐기한다(logout된 토큰도 같은 규칙)
			log.warn("Refresh token reuse detected; revoking session {}", token.get().getSessionId());
			refreshTokenRepository.revokeSession(token.get().getSessionId(), now);
			return new RefreshResult.Failure(AuthErrorCode.AUTH_SESSION_REVOKED);
		}
		return new RefreshResult.Failure(AuthErrorCode.AUTH_INVALID_REFRESH);
	}

	/**
	 * 현재 세션 폐기. 토큰 존재 여부를 노출하지 않기 위해 결과와 상관없이 성공으로 끝난다.
	 */
	@Transactional
	public void logout(String refreshToken) {
		refreshTokenRepository.findByTokenHash(RefreshTokenValues.hash(refreshToken))
				.ifPresent(token -> refreshTokenRepository.revokeSession(token.getSessionId(), clock.instant()));
	}
}
