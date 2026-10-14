package com.barofit.identity.user;

import com.barofit.profile.api.event.ProfileCompleted;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * profile의 온보딩 완료를 받아 로그인 응답의 isOnboarded를 갱신한다.
 * 발행한 쪽의 트랜잭션 안에서 동기로 처리한다(DB가 하나이므로 원자성 보장).
 * 서비스를 분리하면 최종 일관성으로 바뀐다.
 */
@Component
@RequiredArgsConstructor
class OnboardingListener {

	private final UserRepository userRepository;
	private final Clock clock;

	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	public void on(ProfileCompleted event) {
		userRepository.findById(event.userId())
				.filter(User::isActive)
				.ifPresent(user -> user.completeOnboarding(clock.instant()));
	}
}
