package com.barofit.identity.api;

import java.util.Optional;
import java.util.UUID;

/**
 * 다른 모듈이 사용자 정보를 조회할 때 쓰는 공개 인터페이스. 구현은 identity 모듈에 있다.
 */
public interface UserQueryApi {

	Optional<UserSummary> findSummary(UUID userId);
}
