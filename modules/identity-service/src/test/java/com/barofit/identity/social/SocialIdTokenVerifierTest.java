package com.barofit.identity.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barofit.common.error.BusinessException;
import com.barofit.identity.auth.AuthErrorCode;
import com.barofit.identity.token.TestJwtKeyFiles;
import com.barofit.identity.user.SocialProvider;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class SocialIdTokenVerifierTest {

	private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
	private static final String GOOGLE_CLIENT_ID = "google-client.apps.googleusercontent.com";
	private static final String KAKAO_APP_KEY = "kakao-app-key";

	private final KeyPair providerKey = TestJwtKeyFiles.generateKeyPair();

	private final SocialIdTokenVerifier verifier = verifier(List.of(GOOGLE_CLIENT_ID), List.of(KAKAO_APP_KEY));

	@Test
	void 구글_ID_Token에서_sub와_프로필을_꺼낸다() {
		String token = sign(claims -> claims.issuer("https://accounts.google.com").audience(List.of(GOOGLE_CLIENT_ID))
				.claim("name", "홍길동").claim("picture", "https://lh3.googleusercontent.com/a"));

		SocialIdentity identity = verifier.verify(SocialProvider.GOOGLE, token);

		assertThat(identity).isEqualTo(new SocialIdentity(SocialProvider.GOOGLE, "provider-sub", "홍길동",
				"https://lh3.googleusercontent.com/a"));
	}

	@Test
	void 구글은_스킴_없는_issuer도_허용한다() {
		String token = sign(claims -> claims.issuer("accounts.google.com").audience(List.of(GOOGLE_CLIENT_ID)));

		assertThat(verifier.verify(SocialProvider.GOOGLE, token).subject()).isEqualTo("provider-sub");
	}

	@Test
	void 카카오_ID_Token에서_회원번호와_프로필을_꺼낸다() {
		String token = sign(claims -> claims.issuer("https://kauth.kakao.com").audience(List.of(KAKAO_APP_KEY))
				.claim("nickname", "카카오").claim("picture", "https://k.kakaocdn.net/a.jpg"));

		SocialIdentity identity = verifier.verify(SocialProvider.KAKAO, token);

		assertThat(identity).isEqualTo(new SocialIdentity(SocialProvider.KAKAO, "provider-sub", "카카오",
				"https://k.kakaocdn.net/a.jpg"));
	}

	@Test
	void 카카오_http_프로필_사진은_https로_바꾼다() {
		String token = sign(claims -> claims.issuer("https://kauth.kakao.com").audience(List.of(KAKAO_APP_KEY))
				.claim("picture", "http://img1.kakaocdn.net/thumb/R110x110.q70/?fname=default_profile.jpeg"));

		assertThat(verifier.verify(SocialProvider.KAKAO, token).profileImageUrl())
				.isEqualTo("https://img1.kakaocdn.net/thumb/R110x110.q70/?fname=default_profile.jpeg");
	}

	@Test
	void 인가_코드_로그인은_nonce가_같으면_통과한다() {
		String token = sign(claims -> claims.issuer("https://kauth.kakao.com").audience(List.of(KAKAO_APP_KEY))
				.claim("nonce", "nonce-123"));

		assertThat(verifier.verify(SocialProvider.KAKAO, token, "nonce-123").subject()).isEqualTo("provider-sub");
	}

	@Test
	void 인가_코드_로그인은_nonce가_다르거나_없으면_거부한다() {
		String withOtherNonce = sign(claims -> claims.issuer("https://kauth.kakao.com")
				.audience(List.of(KAKAO_APP_KEY)).claim("nonce", "other"));
		String withoutNonce = sign(claims -> claims.issuer("https://kauth.kakao.com").audience(List.of(KAKAO_APP_KEY)));

		for (String token : List.of(withOtherNonce, withoutNonce)) {
			assertThatThrownBy(() -> verifier.verify(SocialProvider.KAKAO, token, "nonce-123"))
					.isInstanceOfSatisfying(BusinessException.class,
							e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_INVALID_ID_TOKEN));
		}
	}

	@Test
	void 인가_코드_교환에_쓰는_client_id는_aud로_자동_허용한다() {
		var kakao = new SocialLoginProperties.Provider("https://kakao.test/jwks", List.of("native-key"),
				"https://kakao.test/token", "rest-api-key", "secret");
		var unconfigured = new SocialLoginProperties.Provider("https://kakao.test/jwks", List.of(" "), null, "", null);

		assertThat(kakao.acceptedAudiences()).containsExactlyInAnyOrder("native-key", "rest-api-key");
		assertThat(kakao.codeExchangeConfigured()).isTrue();
		assertThat(unconfigured.acceptedAudiences()).isEmpty();
		assertThat(unconfigured.codeExchangeConfigured()).isFalse();
	}

	@Test
	void 만료된_토큰은_거부한다() {
		String token = sign(claims -> claims.issuer("https://kauth.kakao.com").audience(List.of(KAKAO_APP_KEY))
				.issuedAt(NOW.minus(Duration.ofHours(2))).expiresAt(NOW.minus(Duration.ofMinutes(2))));

		expectInvalid(SocialProvider.KAKAO, token);
	}

	@Test
	void 다른_provider의_issuer는_거부한다() {
		String token = sign(claims -> claims.issuer("https://accounts.google.com").audience(List.of(KAKAO_APP_KEY)));

		expectInvalid(SocialProvider.KAKAO, token);
	}

	@Test
	void 허용_목록에_없는_aud는_거부한다() {
		String token = sign(claims -> claims.issuer("https://accounts.google.com").audience(List.of("other-app")));

		expectInvalid(SocialProvider.GOOGLE, token);
	}

	@Test
	void aud_허용_목록이_비어_있으면_모두_거부한다() {
		SocialIdTokenVerifier unconfigured = verifier(List.of(), List.of());
		String token = sign(claims -> claims.issuer("https://accounts.google.com").audience(List.of(GOOGLE_CLIENT_ID)));

		assertThatThrownBy(() -> unconfigured.verify(SocialProvider.GOOGLE, token))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void provider가_공개하지_않은_키로_서명한_토큰은_거부한다() {
		String token = sign(TestJwtKeyFiles.generateKeyPair(),
				claims -> claims.issuer("https://accounts.google.com").audience(List.of(GOOGLE_CLIENT_ID)));

		expectInvalid(SocialProvider.GOOGLE, token);
	}

	@Test
	void 형식이_잘못된_토큰은_거부한다() {
		expectInvalid(SocialProvider.GOOGLE, "not-a-jwt");
	}

	private void expectInvalid(SocialProvider provider, String token) {
		assertThatThrownBy(() -> verifier.verify(provider, token))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.AUTH_INVALID_ID_TOKEN));
	}

	private SocialIdTokenVerifier verifier(List<String> googleAudiences, List<String> kakaoAudiences) {
		var jwks = new ImmutableJWKSet<SecurityContext>(new JWKSet(
				new RSAKey.Builder((RSAPublicKey) providerKey.getPublic()).keyID("provider-kid").build()));
		return new SocialIdTokenVerifier(Map.of(
				SocialProvider.GOOGLE,
				SocialIdTokenVerifier.decoder(jwks, SocialIdTokenVerifier.GOOGLE_ISSUERS, googleAudiences, CLOCK),
				SocialProvider.KAKAO,
				SocialIdTokenVerifier.decoder(jwks, SocialIdTokenVerifier.KAKAO_ISSUERS, kakaoAudiences, CLOCK)));
	}

	private String sign(Consumer<JwtClaimsSet.Builder> customizer) {
		return sign(providerKey, customizer);
	}

	private static String sign(KeyPair keyPair, Consumer<JwtClaimsSet.Builder> customizer) {
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.subject("provider-sub")
				.issuedAt(NOW)
				.expiresAt(NOW.plus(Duration.ofHours(1)));
		customizer.accept(claims);
		RSAKey key = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
				.privateKey((RSAPrivateKey) keyPair.getPrivate())
				.keyID("provider-kid")
				.build();
		var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("provider-kid").build();
		return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
	}
}
