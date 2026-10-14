package com.barofit.identity.api.event;

import java.time.Instant;
import java.util.UUID;

/** 회원 탈퇴. 받는 쪽은 커밋 후 자기 데이터를 정리한다 */
public record UserWithdrawn(UUID userId, Instant occurredAt) {
}
