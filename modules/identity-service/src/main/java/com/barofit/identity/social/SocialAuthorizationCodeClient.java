package com.barofit.identity.social;

import com.barofit.common.error.BusinessException;
import com.barofit.identity.auth.AuthErrorCode;
import com.barofit.identity.user.SocialProvider;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 소셜 인가 코드를 ID Token으로 교환한다. 코드·시크릿은 로그에 남기지 않는다 */
@Slf4j
@Component
public class SocialAuthorizationCodeClient {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
	private static final Pattern OAUTH_ERROR = Pattern.compile("\"error\"\\s*:\\s*\"([^\"]+)\"");
	private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
			new ParameterizedTypeReference<>() {
			};

	private final RestClient restClient;
	private final Map<SocialProvider, SocialLoginProperties.Provider> providers;

	@Autowired
	SocialAuthorizationCodeClient(SocialLoginProperties properties) {
		this(RestClient.builder().requestFactory(requestFactory()), properties);
	}

	// 테스트용 builder 주입
	SocialAuthorizationCodeClient(RestClient.Builder builder, SocialLoginProperties properties) {
		this.restClient = builder.build();
		this.providers = new EnumMap<>(SocialProvider.class);
		providers.put(SocialProvider.GOOGLE, properties.google());
		providers.put(SocialProvider.KAKAO, properties.kakao());
		providers.forEach((provider, config) -> {
			if (!config.codeExchangeConfigured()) {
				log.warn("{} 인가 코드 로그인 설정(client-id, client-secret, token-uri)이 없어 해당 로그인을 처리할 수 없습니다",
						provider);
			}
		});
	}

	/** @return 검증 전 ID Token */
	public String exchange(SocialProvider provider, String code, String redirectUri) {
		SocialLoginProperties.Provider config = providers.get(provider);
		if (!config.codeExchangeConfigured()) {
			log.error("{} 인가 코드 교환 설정이 없습니다", provider);
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
		}
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", config.clientId());
		form.add("client_secret", config.clientSecret());
		form.add("redirect_uri", redirectUri);
		form.add("code", code);

		Map<String, Object> response;
		try {
			response = restClient.post()
					.uri(config.tokenUri())
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.accept(MediaType.APPLICATION_JSON)
					.body(form)
					.retrieve()
					.body(JSON_OBJECT);
		} catch (RestClientResponseException e) {
			throw toBusinessException(provider, e);
		} catch (RestClientException e) {
			log.warn("Failed to call {} token endpoint", provider, e);
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
		}

		Object idToken = response == null ? null : response.get("id_token");
		if (!(idToken instanceof String value) || value.isBlank()) {
			// OIDC 꺼짐 또는 openid scope 누락
			log.error("{} token response has no id_token (check OpenID Connect / openid scope settings)", provider);
			throw new BusinessException(AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
		}
		return value;
	}

	private static BusinessException toBusinessException(SocialProvider provider, RestClientResponseException e) {
		if (e.getStatusCode().is5xxServerError()) {
			log.warn("{} token endpoint returned {}", provider, e.getStatusCode().value());
			return new BusinessException(AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
		}
		String error = oauthError(e.getResponseBodyAsString());
		if ("invalid_client".equals(error) || "unauthorized_client".equals(error)) {
			// 서버 설정(클라이언트 ID·시크릿) 오류
			log.error("{} rejected client credentials: {} {}", provider, e.getStatusCode().value(), error);
			return new BusinessException(AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
		}
		// 만료·재사용된 코드, redirect_uri 불일치 등
		log.info("{} rejected authorization code: {} {}", provider, e.getStatusCode().value(), error);
		return new BusinessException(AuthErrorCode.AUTH_INVALID_AUTHORIZATION_CODE);
	}

	private static String oauthError(String body) {
		Matcher matcher = OAUTH_ERROR.matcher(body == null ? "" : body);
		return matcher.find() ? matcher.group(1) : "unknown";
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
		requestFactory.setReadTimeout(READ_TIMEOUT);
		return requestFactory;
	}
}
