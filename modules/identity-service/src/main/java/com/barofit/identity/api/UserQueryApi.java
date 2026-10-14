package com.barofit.identity.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 다른 도메인용 사용자 조회 API. 탈퇴했거나 없는 사용자는 빠진다 */
public interface UserQueryApi {

	Optional<UserSummary> findSummary(UUID userId);

	/** 여러 사용자 일괄 조회. 목록 화면에서는 단건 조회 반복 대신 이것을 쓴다 */
	Map<UUID, UserSummary> findSummaries(Collection<UUID> userIds);
}
