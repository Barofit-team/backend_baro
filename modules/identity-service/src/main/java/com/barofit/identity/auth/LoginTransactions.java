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

/**
 * 로그인과 가입의 트랜잭션 경계. AuthService와 다른 Bean이어야 프록시를 거쳐 트랜잭션이 적용된다.
 */
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
					// 탈퇴하면 social_accounts가 삭제되므로 여기서는 항상 ACTIVE다
					user.recordLogin(now);
					return new LoginResult(sessionTokens.startSession(user, now), user);
				});
	}

	/**
	 * 신규 가입. saveAndFlush로 제약 위반을 이 트랜잭션 안에서 일으키고,
	 * 동시 가입으로 UNIQUE가 깨지면 DataIntegrityViolationException이 호출자에게 전달된다.
	 */
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
