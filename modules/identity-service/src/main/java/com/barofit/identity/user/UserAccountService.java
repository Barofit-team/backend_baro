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

	/**
	 * 탈퇴. 이미 탈퇴했으면 아무것도 하지 않는다(멱등).
	 * 응답이 유실돼 앱이 재시도해도 성공으로 끝나야, 앱이 SDK unlink를 건너뛰지 않는다.
	 * 다른 모듈은 커밋 이후 UserWithdrawn을 받아 자기 데이터를 정리한다.
	 */
	@Transactional
	public void withdraw(UUID userId) {
		User user = userRepository.findById(userId).orElse(null);
		if (user == null || !user.isActive()) {
			return;
		}
		Instant now = clock.instant();
		user.withdraw(now);
		// social_accounts를 지우므로 같은 SNS로 다시 로그인하면 신규 가입이 된다
		socialAccountRepository.deleteAllByUserId(userId);
		refreshTokenRepository.revokeAllByUserId(userId, now);
		// 사용자 프로필 데이터가 생기면 같은 도메인이므로 여기서 함께 삭제한다
		eventPublisher.publishEvent(new UserWithdrawn(userId, now));
	}

	/**
	 * 온보딩(사용자 프로필 입력) 완료. 로그인 응답의 isOnboarded가 true가 된다.
	 * 프로필 저장과 원자적으로 반영되도록 호출하는 쪽의 트랜잭션 안에서만 실행한다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void completeOnboarding(UUID userId) {
		userRepository.findById(userId)
				.filter(User::isActive)
				.ifPresent(user -> user.completeOnboarding(clock.instant()));
	}
}
