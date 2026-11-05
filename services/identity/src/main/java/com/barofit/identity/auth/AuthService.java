package com.barofit.identity.auth;

import com.barofit.common.error.BusinessException;
import com.barofit.common.error.CommonErrorCode;
import com.barofit.identity.image.ProfileImageService;
import com.barofit.identity.social.SocialAuthorizationCodeClient;
import com.barofit.identity.social.SocialIdTokenVerifier;
import com.barofit.identity.social.SocialIdentity;
import com.barofit.identity.social.SocialLoginProperties;
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
	private final SocialAuthorizationCodeClient socialAuthorizationCodeClient;
	private final SocialLoginProperties socialLoginProperties;
	private final LoginTransactions loginTransactions;
	private final ProfileImageService profileImageService;
	private final RefreshTokenRepository refreshTokenRepository;
	private final UserRepository userRepository;
	private final SessionTokens sessionTokens;
	private final Clock clock;

	/** 소셜 로그인·가입. 네트워크 호출이 있어 트랜잭션을 열지 않는다 */
	public LoginResult socialLogin(SocialProvider provider, String idToken) {
		return loginOrRegister(socialIdTokenVerifier.verify(provider, idToken));
	}

	/** 인가 코드 로그인·가입. 서버가 code를 ID Token으로 교환한다 */
	public LoginResult socialLoginWithCode(SocialProvider provider, String code, String redirectUri, String nonce) {
		if (!socialLoginProperties.redirectUris().contains(redirectUri)) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "허용되지 않은 redirectUri입니다.");
		}
		String idToken = socialAuthorizationCodeClient.exchange(provider, code, redirectUri);
		return loginOrRegister(socialIdTokenVerifier.verify(provider, idToken, nonce));
	}

	private LoginResult loginOrRegister(SocialIdentity identity) {
		return loginTransactions.loginExisting(identity)
				.orElseGet(() -> registerOrLoginConcurrently(identity));
	}

	// 동시 가입으로 UNIQUE 위반 시 새 트랜잭션에서 다시 조회한다
	private LoginResult registerOrLoginConcurrently(SocialIdentity identity) {
		LoginResult registered;
		try {
			registered = loginTransactions.register(identity);
		} catch (DataIntegrityViolationException e) {
			log.info("Concurrent sign-up detected for {}; retrying as login", identity.provider());
			return loginTransactions.loginExisting(identity)
					.orElseThrow(() -> new IllegalStateException("가입 충돌 후 재조회에 실패했습니다", e));
		}
		// 가입이 커밋된 뒤 SNS 사진을 저장소로 옮긴다. 실패하면 SNS URL을 그대로 쓴다
		return new LoginResult(registered.tokens(), profileImageService.importSocialImage(registered.user()));
	}

	/** Refresh Token 회전. 동시 요청 중 하나만 성공한다 */
	@Transactional
	public RefreshResult refresh(String refreshToken) {
		Instant now = clock.instant();
		String tokenHash = RefreshTokenValues.hash(refreshToken);
		if (refreshTokenRepository.revokeIfActive(tokenHash, now) == 1) {
			RefreshToken previous = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
			User user = userRepository.findById(previous.getUserId()).orElseThrow();
			// 탈퇴 시 토큰이 모두 폐기되므로 실제로는 오지 않는다
			if (!user.isActive()) {
				return new RefreshResult.Failure(AuthErrorCode.AUTH_USER_WITHDRAWN);
			}
			return new RefreshResult.Success(sessionTokens.rotate(user, previous, now));
		}
		Optional<RefreshToken> token = refreshTokenRepository.findByTokenHash(tokenHash);
		if (token.isPresent() && token.get().isRevoked()) {
			// 폐기된 토큰 재사용: 세션 전체 폐기
			log.warn("Refresh token reuse detected; revoking session {}", token.get().getSessionId());
			refreshTokenRepository.revokeSession(token.get().getSessionId(), now);
			return new RefreshResult.Failure(AuthErrorCode.AUTH_SESSION_REVOKED);
		}
		return new RefreshResult.Failure(AuthErrorCode.AUTH_INVALID_REFRESH);
	}

	/** 세션 폐기. 토큰 존재 여부와 상관없이 성공 */
	@Transactional
	public void logout(String refreshToken) {
		refreshTokenRepository.findByTokenHash(RefreshTokenValues.hash(refreshToken))
				.ifPresent(token -> refreshTokenRepository.revokeSession(token.getSessionId(), clock.instant()));
	}
}
