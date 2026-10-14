package com.barofit.identity.social;

import com.barofit.identity.user.SocialProvider;

/**
 * 검증된 소셜 ID Token에서 꺼낸 정보.
 *
 * @param subject         provider의 고유 ID(Google sub, Kakao 회원번호)
 * @param nickname        NULL 가능
 * @param profileImageUrl NULL 가능
 */
public record SocialIdentity(SocialProvider provider, String subject, String nickname, String profileImageUrl) {
}
