package com.barofit;

import com.barofit.identity.api.BarofitTokenClaims;
import com.barofit.identity.token.TestJwtKeyFiles;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** 테스트용 Access Token. claim을 바꿔 위조·만료 토큰을 만든다 */
public final class TestTokens {

	private TestTokens() {
	}

	public static String accessToken(UUID userId, Instant issuedAt) {
		return accessToken(TestJwtKeyConfiguration.KEYS.keyPair(), userId, issuedAt, claims -> {
		});
	}

	public static String accessToken(KeyPair keyPair, UUID userId, Instant issuedAt,
			Consumer<JwtClaimsSet.Builder> customizer) {
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.issuer(BarofitTokenClaims.ISSUER)
				.audience(List.of(BarofitTokenClaims.AUDIENCE))
				.subject(userId.toString())
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plusSeconds(15 * 60))
				.id(UUID.randomUUID().toString())
				.claim(BarofitTokenClaims.ROLE, "USER");
		customizer.accept(claims);
		TestJwtKeyFiles keys = TestJwtKeyConfiguration.KEYS;
		RSAKey signingKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
				.privateKey((RSAPrivateKey) keyPair.getPrivate())
				.keyID(keys.currentKid())
				.build();
		var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keys.currentKid()).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
	}
}
