package com.barofit.identity.user;

import com.barofit.common.error.BusinessException;
import com.barofit.identity.api.event.UserWithdrawn;
import com.barofit.identity.auth.AuthErrorCode;
import com.barofit.identity.profile.UserProfileRepository;
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
	private final UserProfileRepository userProfileRepository;
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
		String profileImageKey = user.getProfileImageKey();
		user.withdraw(now);
		if (profileImageKey != null) {
			eventPublisher.publishEvent(new ProfileImageDiscarded(profileImageKey));
		}
		// 같은 SNS로 다시 로그인하면 신규 가입
		socialAccountRepository.deleteAllByUserId(userId);
		refreshTokenRepository.revokeAllByUserId(userId, now);
		// 신체 정보는 남기지 않는다
		userProfileRepository.deleteByUserId(userId);
		eventPublisher.publishEvent(new UserWithdrawn(userId, now));
	}

	/** 프로필 사진 교체. 이전 저장소 사진은 커밋 후 지운다 */
	@Transactional
	public User changeProfileImage(UUID userId, ProfileImageSource source, String key, String url) {
		User user = userRepository.findById(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_USER_WITHDRAWN));
		String previousKey = user.changeProfileImage(source, key, url, clock.instant());
		if (previousKey != null && !previousKey.equals(key)) {
			eventPublisher.publishEvent(new ProfileImageDiscarded(previousKey));
		}
		return user;
	}

	/** 온보딩 완료. 호출하는 쪽 트랜잭션 안에서만 실행 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void completeOnboarding(UUID userId) {
		userRepository.findById(userId)
				.filter(User::isActive)
				.ifPresent(user -> user.completeOnboarding(clock.instant()));
	}
}
