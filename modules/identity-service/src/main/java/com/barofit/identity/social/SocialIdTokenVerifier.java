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

/** 소셜 ID Token(OIDC) 검증. 디코더는 Bean으로 등록하지 않는다 */
@Slf4j
@Component
public class SocialIdTokenVerifier {

	static final Set<String> GOOGLE_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");
	static final Set<String> KAKAO_ISSUERS = Set.of("https://kauth.kakao.com");

	private static final String NONCE_CLAIM = "nonce";
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

	// 테스트용 디코더 주입
	SocialIdTokenVerifier(Map<SocialProvider, JwtDecoder> decoders) {
		this.decoders = new EnumMap<>(decoders);
	}

	public SocialIdentity verify(SocialProvider provider, String idToken) {
		return toIdentity(provider, decode(provider, idToken));
	}

	/** 인가 코드 로그인용. nonce까지 확인한다 */
	public SocialIdentity verify(SocialProvider provider, String idToken, String expectedNonce) {
		Jwt jwt = decode(provider, idToken);
		if (expectedNonce == null || !expectedNonce.equals(jwt.getClaimAsString(NONCE_CLAIM))) {
			log.info("Rejected {} ID token: nonce mismatch", provider);
			throw new BusinessException(AuthErrorCode.AUTH_INVALID_ID_TOKEN);
		}
		return toIdentity(provider, jwt);
	}

	private Jwt decode(SocialProvider provider, String idToken) {
		Jwt jwt;
		try {
			jwt = decoders.get(provider).decode(idToken);
		} catch (BadJwtException e) {
			log.info("Rejected {} ID token: {}", provider, e.getMessage());
			throw new BusinessException(AuthErrorCode.AUTH_INVALID_ID_TOKEN);
		} catch (JwtException e) {
			// 공개키 조회 실패 등
			log.warn("Failed to verify {} ID token", provider, e);
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
		}
		return jwt;
	}

	private static SocialIdentity toIdentity(SocialProvider provider, Jwt jwt) {
		return switch (provider) {
			case GOOGLE -> new SocialIdentity(provider, jwt.getSubject(),
					jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"));
			// 카카오 기본 사진은 http라 https로 바꾼다
			case KAKAO -> new SocialIdentity(provider, jwt.getSubject(),
					jwt.getClaimAsString("nickname"), toHttps(jwt.getClaimAsString("picture")));
		};
	}

	private static String toHttps(String url) {
		return url != null && url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
	}

	static JwtDecoder decoder(JWKSource<SecurityContext> jwkSource, Set<String> issuers, Collection<String> audiences,
			Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(jwkSource).build();
		decoder.setJwtValidator(validator(issuers, audiences, clock));
		return decoder;
	}

	// timestamp 검증기를 빼면 exp 검증이 사라진다
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
		decoder.setJwtValidator(validator(issuers, provider.acceptedAudiences(), clock));
		return decoder;
	}

	private static RestTemplate jwksRestTemplate() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(JWKS_CONNECT_TIMEOUT);
		requestFactory.setReadTimeout(JWKS_READ_TIMEOUT);
		return new RestTemplate(requestFactory);
	}

	private static void warnIfUnconfigured(SocialProvider provider, SocialLoginProperties.Provider properties) {
		if (properties.acceptedAudiences().isEmpty()) {
			log.warn("{} 로그인의 aud 허용 목록이 비어 있어 모든 {} ID Token을 거부합니다", provider, provider);
		}
	}
}
