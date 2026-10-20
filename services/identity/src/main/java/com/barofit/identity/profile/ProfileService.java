package com.barofit.identity.profile;

import com.barofit.common.error.BusinessException;
import com.barofit.common.error.CommonErrorCode;
import com.barofit.identity.auth.AuthErrorCode;
import com.barofit.identity.user.User;
import com.barofit.identity.user.UserAccountService;
import com.barofit.identity.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

	private final UserRepository userRepository;
	private final UserProfileRepository userProfileRepository;
	private final UserAccountService userAccountService;
	private final NicknameService nicknameService;
	private final Clock clock;

	/** 온보딩 저장. 닉네임 확정, 프로필 저장, onboarded=true를 한 트랜잭션에서 한다 */
	@Transactional
	public MyAccount onboard(UUID userId, OnboardingCommand command) {
		validate(command);
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
		user.changeNickname(command.nickname(), now);
		userAccountService.completeOnboarding(userId);
		UserProfile profile = userProfileRepository.save(UserProfile.create(userId, command, now));
		try {
			// 다른 사용자와 같은 닉네임을 동시에 확정하면 유니크 인덱스가 막는다
			userRepository.flush();
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ProfileErrorCode.PROFILE_NICKNAME_DUPLICATED);
		}
		return new MyAccount(user, Optional.of(profile));
	}

	@Transactional(readOnly = true)
	public MyAccount getMyAccount(UUID userId) {
		User user = userRepository.findById(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_USER_WITHDRAWN));
		return new MyAccount(user, userProfileRepository.findById(userId));
	}

	// 형식은 요청 DTO가 검증하고, 여기서는 닉네임 규칙과 값 사이 관계를 본다
	private void validate(OnboardingCommand command) {
		if (!NicknamePolicy.isValid(command.nickname())) {
			throw new BusinessException(ProfileErrorCode.PROFILE_NICKNAME_INVALID);
		}
		int currentYear = clock.instant().atZone(ZoneOffset.UTC).getYear();
		if (command.birthYear() > currentYear) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "출생연도는 올해 이후일 수 없습니다.");
		}
		if (command.skeletalMuscleKg() != null && command.skeletalMuscleKg().compareTo(command.weightKg()) >= 0) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "골격근량은 몸무게보다 작아야 합니다.");
		}
	}
}
