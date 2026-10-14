package com.barofit.identity.social;

import com.barofit.common.error.BusinessException;
import com.barofit.identity.auth.AuthErrorCode;
import com.barofit.identity.user.SocialProvider;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 앱이 SDK로 받은 소셜 ID Token(OIDC)을 검증한다.
 * provider별 디코더는 @Bean으로 등록하지 않는다. Bean으로 두면 Resource Server가 바로핏 JWT 검증에 잘못 쓸 수 있다.
 * nonce는 MVP에서 검증하지 않는다(탈취된 ID Token 재사용은 막지 못함을 감수).
 */
@Slf4j
@Component
public class SocialIdTokenVerifier {

	static final Set<String> GOOGLE_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
	static final Set<String> KAKAO_ISSUERS = Set.of("https://kauth.kakao.com");

	private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);
	private static final Duration JWKS_CONNECT_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration JWKS_READ_TIMEOUT = Duration.ofSeconds(5);

	private final Map<SocialProvider, JwtDecoder> decoders;

	@Autowired
	SocialIdTokenVerifier(SocialLoginProperties properties, Clock clock) {
		RestTemplate restTemplate = jwksRestTemplate();
		this.decoders = new EnumMap<>(SocialProvider.class);
		decoders.put(SocialProvider.GOOGLE, remoteDecoder(properties.google(), GOOGLE_ISSUERS, restTemplate, clock));
		decoders.put(SocialProvider.KAKAO, remoteDecoder(properties.kakao(), KAKAO_ISSUERS, restTemplate, clock));
		warnIfUnconfigured(SocialProvider.GOOGLE, properties.google());
		warnIfUnconfigured(SocialProvider.KAKAO, properties.kakao());
	}

	// 테스트용: provider별 디코더를 직접 주입한다
	SocialIdTokenVerifier(Map<SocialProvider, JwtDecoder> decoders) {
		this.decoders = new EnumMap<>(decoders);
	}

	public SocialIdentity verify(SocialProvider provider, String idToken) {
		Jwt jwt;
		try {
			jwt = decoders.get(provider).decode(idToken);
		} catch (BadJwtException e) {
			log.info("Rejected {} ID token: {}", provider, e.getMessage());
			throw new BusinessException(AuthErrorCode.AUTH_INVALID_ID_TOKEN);
		} catch (JwtException e) {
			// 서명·claim 문제가 아닌 실패(공개키 조회 실패 등)
			log.warn("Failed to verify {} ID token", provider, e);
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
		}
		return switch (provider) {
			case GOOGLE -> new SocialIdentity(provider, jwt.getSubject(),
					jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"));
			case KAKAO -> new SocialIdentity(provider, jwt.getSubject(),
					jwt.getClaimAsString("nickname"), jwt.getClaimAsString("picture"));
		};
	}

	static JwtDecoder decoder(JWKSource<SecurityContext> jwkSource, Set<String> issuers, Collection<String> audiences,
			Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(jwkSource).build();
		decoder.setJwtValidator(validator(issuers, audiences, clock));
		return decoder;
	}

	// 검증기를 직접 조합하므로 timestamp 검증기를 빠뜨리면 exp 검증이 사라진다
	private static OAuth2TokenValidator<Jwt> validator(Set<String> issuers, Collection<String> audiences, Clock clock) {
		JwtTimestampValidator timestamp = new JwtTimestampValidator(CLOCK_SKEW);
		timestamp.setAllowEmptyExpiryClaim(false);
		timestamp.setClock(clock);
		Set<String> allowedAudiences = Set.copyOf(audiences);
		return new DelegatingOAuth2TokenValidator<>(List.of(
				timestamp,
				new JwtClaimValidator<Object>(JwtClaimNames.ISS, iss -> iss != null && issuers.contains(iss.toString())),
				new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
						aud -> aud != null && aud.stream().anyMatch(allowedAudiences::contains)),
				new JwtClaimValidator<String>(JwtClaimNames.SUB, sub -> sub != null && !sub.isBlank())));
	}

	private static JwtDecoder remoteDecoder(SocialLoginProperties.Provider provider, Set<String> issuers,
			RestTemplate restTemplate, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(provider.jwkSetUri())
				.restOperations(restTemplate)
				.build();
		decoder.setJwtValidator(validator(issuers, provider.audiences(), clock));
		return decoder;
	}

	private static RestTemplate jwksRestTemplate() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(JWKS_CONNECT_TIMEOUT);
		requestFactory.setReadTimeout(JWKS_READ_TIMEOUT);
		return new RestTemplate(requestFactory);
	}

	private static void warnIfUnconfigured(SocialProvider provider, SocialLoginProperties.Provider properties) {
		if (properties.audiences().isEmpty()) {
			log.warn("{} 로그인의 aud 허용 목록이 비어 있어 모든 {} ID Token을 거부합니다", provider, provider);
		}
	}
}
