package com.barofit.identity.user;

import com.barofit.identity.api.event.UserWithdrawn;
import com.barofit.identity.token.RefreshTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAccountService {

	private final UserRepository userRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	/** 탈퇴(멱등). 다른 모듈은 커밋 후 UserWithdrawn으로 정리한다 */
	@Transactional
	public void withdraw(UUID userId) {
		User user = userRepository.findById(userId).orElse(null);
		if (user == null || !user.isActive()) {
			return;
		}
		Instant now = clock.instant();
		user.withdraw(now);
		// 같은 SNS로 다시 로그인하면 신규 가입
		socialAccountRepository.deleteAllByUserId(userId);
		refreshTokenRepository.revokeAllByUserId(userId, now);
		// 프로필 데이터가 생기면 여기서 함께 삭제
		eventPublisher.publishEvent(new UserWithdrawn(userId, now));
	}

	/** 온보딩 완료. 호출하는 쪽 트랜잭션 안에서만 실행 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void completeOnboarding(UUID userId) {
		userRepository.findById(userId)
				.filter(User::isActive)
				.ifPresent(user -> user.completeOnboarding(clock.instant()));
	}
}
