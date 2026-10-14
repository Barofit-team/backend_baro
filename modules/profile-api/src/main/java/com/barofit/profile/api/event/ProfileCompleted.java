package com.barofit.profile.api.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 온보딩(운동 프로필 입력) 완료. identity는 같은 트랜잭션의 동기 리스너로 받아 onboarded를 갱신한다.
 */
public record ProfileCompleted(UUID userId, Instant occurredAt) {
}
