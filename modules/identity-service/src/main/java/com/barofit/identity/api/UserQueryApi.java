package com.barofit.identity.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 다른 도메인이 사용자 정보를 조회할 때 쓰는 공개 인터페이스. 구현은 identity-service에 있다.
 * 탈퇴했거나 없는 사용자는 결과에서 빠진다.
 */
public interface UserQueryApi {

	Optional<UserSummary> findSummary(UUID userId);

	/**
	 * 여러 사용자를 한 번에 조회한다. 목록 화면처럼 여러 사용자의 정보가 필요하면 단건 조회를 반복하지 말고 이것을 쓴다.
	 * 서비스 분리 후 HTTP 호출 한 번으로 바뀌는 자리다(단건 반복은 호출 수 폭증으로 이어진다).
	 *
	 * @param userIds 중복이 있어도 된다
	 * @return 사용자 ID를 키로 하는 Map. 탈퇴했거나 없는 사용자는 키가 없다
	 */
	Map<UUID, UserSummary> findSummaries(Collection<UUID> userIds);
}
