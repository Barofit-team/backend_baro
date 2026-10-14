package com.barofit.identity.token;

import com.barofit.identity.api.BarofitTokenClaims;
import com.barofit.identity.user.UserRole;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Access Token 발급. 개인정보는 넣지 않는다 */
@Component
public class AccessTokenIssuer {

	private final JwtEncoder jwtEncoder;
	private final String currentKid;
	private final Clock clock;

	AccessTokenIssuer(JwtEncoder barofitJwtEncoder, JwtKeys jwtKeys, Clock clock) {
		this.jwtEncoder = barofitJwtEncoder;
		this.currentKid = jwtKeys.currentKid();
		this.clock = clock;
	}

	public IssuedAccessToken issue(UUID userId, UserRole role) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(TokenPolicy.ACCESS_TOKEN_TTL);
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(BarofitTokenClaims.ISSUER)
				.audience(List.of(BarofitTokenClaims.AUDIENCE))
				.subject(userId.toString())
				.issuedAt(now)
				.expiresAt(expiresAt)
				.id(UUID.randomUUID().toString())
				.claim(BarofitTokenClaims.ROLE, role.name())
				.build();
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(currentKid).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedAccessToken(token, expiresAt);
	}

	public record IssuedAccessToken(String value, Instant expiresAt) {
	}
}
