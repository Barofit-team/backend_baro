package com.barofit.identity.api.event;

import java.time.Instant;
import java.util.UUID;

/** 회원 가입. 가입 트랜잭션 안에서 발행 */
public record UserRegistered(UUID userId, Instant occurredAt) {
}
