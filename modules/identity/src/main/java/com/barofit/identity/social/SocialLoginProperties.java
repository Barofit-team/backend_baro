package com.barofit.identity.social;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소셜 ID Token 검증 설정. aud 허용 목록이 비어 있는 provider는 모든 토큰을 거부한다.
 *
 * @param google aud: 웹/Android/iOS 클라이언트 ID
 * @param kakao  aud: 앱 키
 */
@ConfigurationProperties("barofit.social")
public record SocialLoginProperties(Provider google, Provider kakao) {

	public record Provider(String jwkSetUri, List<String> audiences) {

		public Provider {
			audiences = audiences == null
					? List.of()
					: audiences.stream().filter(audience -> !audience.isBlank()).toList();
		}
	}
}
