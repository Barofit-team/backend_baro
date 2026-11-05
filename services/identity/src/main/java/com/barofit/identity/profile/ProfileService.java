package com.barofit.identity.profile;

import com.barofit.common.error.BusinessException;
import com.barofit.common.error.CommonErrorCode;
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
		flushNickname();
		return new MyAccount(user, Optional.of(profile), currentYear);
	}

	/** 온보딩 후 프로필 부분 수정. 보낸 항목만 바꾼다 */
	@Transactional
	public MyAccount updateProfile(UUID userId, ProfileUpdateCommand command) {
		if (command.isEmpty()) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, "변경할 항목을 하나 이상 보내 주세요.");
		}
		if (command.nickname() != null && !NicknamePolicy.isValid(command.nickname())) {
			throw new BusinessException(ProfileErrorCode.PROFILE_NICKNAME_INVALID);
		}
		// 같은 사용자의 동시 수정은 여기서 줄을 선다
		User user = userRepository.findByIdForUpdate(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_USER_WITHDRAWN));
		if (!user.isOnboarded()) {
			throw new BusinessException(ProfileErrorCode.PROFILE_NOT_ONBOARDED);
		}
		UserProfile profile = userProfileRepository.findById(userId)
				.orElseThrow(() -> new IllegalStateException("온보딩한 사용자의 프로필이 없습니다: " + userId));
		Instant now = clock.instant();
		int currentYear = currentYear();
		// 대소문자만 바꾸는 경우는 자기 닉네임이므로 중복이 아니다
		if (command.nickname() != null && !command.nickname().equalsIgnoreCase(user.getNickname())) {
			if (nicknameService.isTaken(command.nickname())) {
				throw new BusinessException(ProfileErrorCode.PROFILE_NICKNAME_DUPLICATED);
			}
		}
		if (command.nickname() != null && !command.nickname().equals(user.getNickname())) {
			user.changeNickname(command.nickname(), now);
		}
		profile.update(command, currentYear, now);
		flushNickname();
		return new MyAccount(user, Optional.of(profile), currentYear);
	}

	@Transactional(readOnly = true)
	public MyAccount getMyAccount(UUID userId) {
		User user = userRepository.findById(userId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_USER_WITHDRAWN));
		return new MyAccount(user, userProfileRepository.findById(userId), currentYear());
	}

	// 다른 사용자와 같은 닉네임을 동시에 확정하면 유니크 인덱스가 막는다
	private void flushNickname() {
		try {
			userRepository.flush();
		} catch (DataIntegrityViolationException e) {
			if (violates(e, NICKNAME_UNIQUE_INDEX)) {
				throw new BusinessException(ProfileErrorCode.PROFILE_NICKNAME_DUPLICATED);
			}
			throw e;
		}
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
