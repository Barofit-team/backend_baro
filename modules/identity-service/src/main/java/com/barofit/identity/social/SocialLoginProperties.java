package com.barofit.identity.social;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 소셜 로그인 설정. aud 허용 목록이 비면 해당 provider는 모두 거부 */
@ConfigurationProperties("barofit.social")
public record SocialLoginProperties(Provider google, Provider kakao, List<String> redirectUris) {

	public SocialLoginProperties {
		redirectUris = nonBlank(redirectUris);
	}

	/** clientId·clientSecret은 인가 코드 교환용. 시크릿은 서버에만 둔다 */
	public record Provider(String jwkSetUri, List<String> audiences, String tokenUri, String clientId,
			String clientSecret) {

		public Provider {
			audiences = nonBlank(audiences);
		}

		/** 교환에 쓴 clientId도 aud로 허용 */
		public Set<String> acceptedAudiences() {
			Set<String> accepted = new LinkedHashSet<>(audiences);
			if (hasText(clientId)) {
				accepted.add(clientId);
			}
			return Set.copyOf(accepted);
		}

		public boolean codeExchangeConfigured() {
			return hasText(tokenUri) && hasText(clientId) && hasText(clientSecret);
		}

		// 시크릿 마스킹
		@Override
		public String toString() {
			return "Provider[jwkSetUri=" + jwkSetUri + ", audiences=" + audiences + ", tokenUri=" + tokenUri
					+ ", clientId=" + clientId + ", clientSecret=" + (hasText(clientSecret) ? "****" : "") + "]";
		}
	}

	private static List<String> nonBlank(List<String> values) {
		return values == null ? List.of() : values.stream().filter(SocialLoginProperties::hasText).toList();
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
