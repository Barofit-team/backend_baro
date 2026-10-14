package com.barofit.identity.user;

import com.barofit.identity.api.UserQueryApi;
import com.barofit.identity.api.UserSummary;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class UserQueryService implements UserQueryApi {

	private final UserRepository userRepository;

	/**
	 * 탈퇴한 사용자는 없는 사용자로 취급한다.
	 */
	@Override
	@Transactional(readOnly = true)
	public Optional<UserSummary> findSummary(UUID userId) {
		return userRepository.findById(userId)
				.filter(User::isActive)
				.map(user -> new UserSummary(user.getId(), user.getNickname(), user.getProfileImageUrl()));
	}
}
