package com.barofit.identity.api.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 회원 탈퇴. 다른 모듈은 커밋 이후(AFTER_COMMIT) 새 트랜잭션에서 자기 데이터를 정리한다.
 */
public record UserWithdrawn(UUID userId, Instant occurredAt) {
}
