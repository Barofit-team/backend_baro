package com.barofit.identity.api;

import java.util.UUID;

/** nickname, profileImageUrl은 NULL 가능 */
public record UserSummary(UUID id, String nickname, String profileImageUrl) {
}
