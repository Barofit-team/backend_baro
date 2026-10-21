package com.barofit.identity.profile;

import com.barofit.common.error.BusinessException;
import com.barofit.identity.auth.AuthErrorCode;
import com.barofit.identity.user.User;
import com.barofit.identity.user.UserAccountService;
import com.barofit.identity.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

	/** 나이 계산 기준 시간대 */
	static final ZoneId AGE_ZONE = ZoneId.of("Asia/Seoul");

	private static final String NICKNAME_UNIQUE_INDEX = "uk_users_nickname_onboarded";

	private final UserRepository userRepository;
	private final UserProfileRepository userProfileRepository;
	private final UserAccountService userAccountService;
	private final NicknameService nicknameService;
	private final Clock clock;

	/** 온보딩 저장. 닉네임 확정, 프로필 저장, onboarded=true를 한 트랜잭션에서 한다 */
	@Transactional
	public MyAccount onboard(UUID userId, OnboardingCommand command) {
		if (!NicknamePolicy.isValid(command.nickname())) {
			throw new BusinessException(ProfileErrorCode.PROFILE_NICKNAME_INVALID);
		}
		// 같은 사용자의 동시 요청은 여기서 줄을 선다
		User user = userRepository.findByIdForUpdate(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_USER_WITHDRAWN));
		if (user.isOnboarded()) {
			throw new BusinessException(ProfileErrorCode.PROFILE_ALREADY_ONBOARDED);
		}
		if (nicknameService.isTaken(command.nickname())) {
			throw new BusinessException(ProfileErrorCode.PROFILE_NICKNAME_DUPLICATED);
		}
		Instant now = clock.instant();
		int currentYear = currentYear();
		user.changeNickname(command.nickname(), now);
		userAccountService.completeOnboarding(userId);
		UserProfile profile = userProfileRepository.save(UserProfile.create(userId, command, currentYear, now));
		try {
			// 다른 사용자와 같은 닉네임을 동시에 확정하면 유니크 인덱스가 막는다
			userRepository.flush();
		} catch (DataIntegrityViolationException e) {
			if (violates(e, NICKNAME_UNIQUE_INDEX)) {
				throw new BusinessException(ProfileErrorCode.PROFILE_NICKNAME_DUPLICATED);
			}
			throw e;
		}
		return new MyAccount(user, Optional.of(profile), currentYear);
	}

	@Transactional(readOnly = true)
	public MyAccount getMyAccount(UUID userId) {
		User user = userRepository.findById(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_USER_WITHDRAWN));
		return new MyAccount(user, userProfileRepository.findById(userId), currentYear());
	}

	private int currentYear() {
		return clock.instant().atZone(AGE_ZONE).getYear();
	}

	private static boolean violates(DataIntegrityViolationException e, String constraintName) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return constraintName.equalsIgnoreCase(violation.getConstraintName());
			}
		}
		return false;
	}
}
