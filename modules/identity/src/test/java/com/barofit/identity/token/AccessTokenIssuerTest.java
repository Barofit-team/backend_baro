package com.barofit.identity.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.barofit.identity.user.UserRole;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AccessTokenIssuerTest {

	private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");

	@Test
	void 설계된_claim과_kid로_15분짜리_토큰을_발급한다() {
		TestJwtKeyFiles files = TestJwtKeyFiles.create("kid-1");
		JwtKeys keys = JwtKeys.load(new JwtKeyProperties(
				files.privateKeyPath().toString(), files.publicJwksPath().toString(), "kid-1"));
		var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(keys.getSigningKey())));
		var issuer = new AccessTokenIssuer(encoder, keys, Clock.fixed(NOW, ZoneOffset.UTC));
		UUID userId = UUID.randomUUID();

		var issued = issuer.issue(userId, UserRole.USER);

		// 서명만 검증하고 시간 검증은 끈다(고정 시각으로 발급했기 때문)
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(new ImmutableJWKSet<>(keys.getPublicJwks())).build();
		decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
		Jwt jwt = decoder.decode(issued.value());
		assertThat(jwt.getHeaders()).containsEntry("kid", "kid-1").containsEntry("alg", "RS256");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("barofit-identity");
		assertThat(jwt.getAudience()).containsExactly("barofit-api");
		assertThat(jwt.getSubject()).isEqualTo(userId.toString());
		assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
		assertThat(jwt.getId()).isNotBlank();
		assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
		assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plusSeconds(15 * 60)).isEqualTo(issued.expiresAt());
		assertThat(jwt.getClaims()).doesNotContainKeys("nickname", "email");
	}
}
