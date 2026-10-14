package com.barofit.identity.user;

import com.barofit.identity.api.UserQueryApi;
import com.barofit.identity.api.UserSummary;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class UserQueryService implements UserQueryApi {

	private final UserRepository userRepository;

	/** 탈퇴한 사용자는 없는 사용자로 취급 */
	@Override
	@Transactional(readOnly = true)
	public Optional<UserSummary> findSummary(UUID userId) {
		return userRepository.findById(userId)
				.filter(User::isActive)
				.map(UserQueryService::toSummary);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<UUID, UserSummary> findSummaries(Collection<UUID> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		return userRepository.findAllById(new HashSet<>(userIds)).stream()
				.filter(User::isActive)
				.map(UserQueryService::toSummary)
				.collect(Collectors.toUnmodifiableMap(UserSummary::id, Function.identity()));
	}

	private static UserSummary toSummary(User user) {
		return new UserSummary(user.getId(), user.getNickname(), user.getProfileImageUrl());
	}
}
