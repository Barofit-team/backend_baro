package com.barofit.identity.profile;

import com.barofit.common.error.BusinessException;
import com.barofit.identity.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 닉네임 중복 확인·랜덤 생성. 예약하지 않으므로 최종 확정은 온보딩 저장 시점이다 */
@Service
@RequiredArgsConstructor
public class NicknameService {

	static final int MAX_GENERATION_ATTEMPTS = 10;

	private final UserRepository userRepository;
	private final RandomNicknameGenerator generator;

	@Transactional(readOnly = true)
	public NicknameAvailability check(String nickname) {
		if (!NicknamePolicy.isValid(nickname)) {
			return NicknameAvailability.INVALID_FORMAT;
		}
		return isTaken(nickname) ? NicknameAvailability.DUPLICATED : NicknameAvailability.AVAILABLE;
	}

	@Transactional(readOnly = true)
	public String generate() {
		for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
			String nickname = generator.next();
			if (!isTaken(nickname)) {
				return nickname;
			}
		}
		throw new BusinessException(ProfileErrorCode.PROFILE_NICKNAME_GENERATION_FAILED);
	}

	boolean isTaken(String nickname) {
		return userRepository.existsOnboardedNickname(nickname);
	}
}
