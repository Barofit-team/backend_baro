package com.barofit.identity.api.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 신규 회원 가입. 가입 트랜잭션 안에서 발행된다.
 */
public record UserRegistered(UUID userId, Instant occurredAt) {
}
