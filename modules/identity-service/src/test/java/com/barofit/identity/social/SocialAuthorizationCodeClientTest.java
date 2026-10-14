package com.barofit.identity.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.barofit.common.error.BusinessException;
import com.barofit.identity.auth.AuthErrorCode;
import com.barofit.identity.user.SocialProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class SocialAuthorizationCodeClientTest {

	private static final String KAKAO_TOKEN_URI = "https://kauth.kakao.test/oauth/token";
	private static final String GOOGLE_TOKEN_URI = "https://oauth2.google.test/token";
	private static final String REDIRECT_URI = "http://localhost:8081";

	private final RestClient.Builder builder = RestClient.builder();
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	@Test
	void 인가_코드를_ID_Token으로_교환한다() {
		SocialAuthorizationCodeClient client = client("kakao-secret");
		MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
		expectedForm.add("grant_type", "authorization_code");
		expectedForm.add("client_id", "kakao-rest-key");
		expectedForm.add("client_secret", "kakao-secret");
		expectedForm.add("redirect_uri", REDIRECT_URI);
		expectedForm.add("code", "auth-code");
		server.expect(requestTo(KAKAO_TOKEN_URI))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
				.andExpect(content().formData(expectedForm))
				.andRespond(withSuccess("""
						{"access_token":"a","token_type":"bearer","id_token":"kakao.id.token","expires_in":21599}
						""", MediaType.APPLICATION_JSON));

		assertThat(client.exchange(SocialProvider.KAKAO, "auth-code", REDIRECT_URI)).isEqualTo("kakao.id.token");
		server.verify();
	}

	@Test
	void 구글은_구글_토큰_엔드포인트로_교환한다() {
		SocialAuthorizationCodeClient client = client("kakao-secret");
		server.expect(requestTo(GOOGLE_TOKEN_URI))
				.andRespond(withSuccess("{\"id_token\":\"google.id.token\"}", MediaType.APPLICATION_JSON));

		assertThat(client.exchange(SocialProvider.GOOGLE, "auth-code", REDIRECT_URI)).isEqualTo("google.id.token");
	}

	@Test
	void 만료되거나_재사용된_코드는_AUTH_INVALID_AUTHORIZATION_CODE() {
		SocialAuthorizationCodeClient client = client("kakao-secret");
		server.expect(requestTo(KAKAO_TOKEN_URI)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"invalid_grant\",\"error_description\":\"authorization code not found\",\"error_code\":\"KOE320\"}"));

		expectError(client, AuthErrorCode.AUTH_INVALID_AUTHORIZATION_CODE);
	}

	@Test
	void 클라이언트_시크릿이_틀리면_서버_설정_문제로_503() {
		SocialAuthorizationCodeClient client = client("kakao-secret");
		server.expect(requestTo(KAKAO_TOKEN_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"invalid_client\",\"error_code\":\"KOE010\"}"));

		expectError(client, AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
	}

	@Test
	void 소셜_서버_오류는_503() {
		SocialAuthorizationCodeClient client = client("kakao-secret");
		server.expect(requestTo(KAKAO_TOKEN_URI)).andRespond(withServerError());

		expectError(client, AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
	}

	@Test
	void 응답에_id_token이_없으면_503() {
		SocialAuthorizationCodeClient client = client("kakao-secret");
		server.expect(requestTo(KAKAO_TOKEN_URI))
				.andRespond(withSuccess("{\"access_token\":\"a\"}", MediaType.APPLICATION_JSON));

		expectError(client, AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
	}

	@Test
	void 시크릿이_설정되지_않은_provider는_호출하지_않고_503() {
		SocialAuthorizationCodeClient client = client("");

		expectError(client, AuthErrorCode.AUTH_SOCIAL_PROVIDER_UNAVAILABLE);
		server.verify();
	}

	@Test
	void 설정을_문자열로_바꿔도_시크릿은_드러나지_않는다() {
		SocialLoginProperties.Provider provider = new SocialLoginProperties.Provider(
				"https://kakao.test/jwks", List.of(), KAKAO_TOKEN_URI, "kakao-rest-key", "kakao-secret");

		assertThat(provider.toString()).doesNotContain("kakao-secret").contains("kakao-rest-key", "****");
	}

	private void expectError(SocialAuthorizationCodeClient client, AuthErrorCode expected) {
		assertThatThrownBy(() -> client.exchange(SocialProvider.KAKAO, "auth-code", REDIRECT_URI))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(expected));
	}

	private SocialAuthorizationCodeClient client(String kakaoSecret) {
		SocialLoginProperties properties = new SocialLoginProperties(
				new SocialLoginProperties.Provider("https://google.test/certs", List.of(), GOOGLE_TOKEN_URI,
						"google-web-client", "google-secret"),
				new SocialLoginProperties.Provider("https://kakao.test/jwks", List.of(), KAKAO_TOKEN_URI,
						"kakao-rest-key", kakaoSecret),
				List.of(REDIRECT_URI));
		return new SocialAuthorizationCodeClient(builder, properties);
	}
}
