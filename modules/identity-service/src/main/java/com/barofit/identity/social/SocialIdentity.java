package com.barofit.identity.social;

import com.barofit.identity.user.SocialProvider;

/** 검증된 ID Token 정보. subject는 provider 고유 ID, 나머지는 NULL 가능 */
public record SocialIdentity(SocialProvider provider, String subject, String nickname, String profileImageUrl) {
}
