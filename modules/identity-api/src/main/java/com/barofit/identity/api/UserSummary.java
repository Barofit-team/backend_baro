package com.barofit.identity.api;

import java.util.UUID;

/**
 * @param nickname        NULL 가능
 * @param profileImageUrl NULL 가능. 외부 URL이라 깨질 수 있다
 */
public record UserSummary(UUID id, String nickname, String profileImageUrl) {
}
