package com.barofit.identity.auth;

import com.barofit.identity.api.event.UserRegistered;
import com.barofit.identity.social.SocialIdentity;
import com.barofit.identity.token.SessionTokens;
import com.barofit.identity.user.SocialAccount;
import com.barofit.identity.user.SocialAccountRepository;
import com.barofit.identity.user.User;
import com.barofit.identity.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 로그인·가입 트랜잭션. 프록시 적용을 위해 AuthService와 분리 */
@Component
@RequiredArgsConstructor
class LoginTransactions {

	private final UserRepository userRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final SessionTokens sessionTokens;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	@Transactional
	public Optional<LoginResult> loginExisting(SocialIdentity identity) {
		return socialAccountRepository.findByProviderAndProviderUserId(identity.provider(), identity.subject())
				.map(account -> {
					Instant now = clock.instant();
					User user = userRepository.findById(account.getUserId()).orElseThrow();
					// 탈퇴하면 social_accounts가 지워지므로 항상 ACTIVE
					user.recordLogin(now);
					return new LoginResult(sessionTokens.startSession(user, now), user);
				});
	}

	/** 신규 가입. 동시 가입 시 DataIntegrityViolationException */
	@Transactional
	public LoginResult register(SocialIdentity identity) {
		Instant now = clock.instant();
		User user = userRepository.saveAndFlush(User.register(identity.nickname(), identity.profileImageUrl(), now));
		socialAccountRepository.saveAndFlush(
				new SocialAccount(user.getId(), identity.provider(), identity.subject(), now));
		eventPublisher.publishEvent(new UserRegistered(user.getId(), now));
		return new LoginResult(sessionTokens.startSession(user, now), user);
	}
}
