package com.barofit.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/** exp·nbf 검증. 만료는 별도 코드로 구분하고 미래 iat는 거부한다 */
class AccessTokenTimestampValidator implements OAuth2TokenValidator<Jwt> {

	static final String EXPIRED_ERROR_CODE = "barofit_token_expired";

	private final JwtTimestampValidator delegate;
	private final Duration clockSkew;
	private final Clock clock;

	AccessTokenTimestampValidator(Duration clockSkew, Clock clock) {
		this.delegate = new JwtTimestampValidator(clockSkew);
		this.delegate.setAllowEmptyExpiryClaim(false);
		this.delegate.setClock(clock);
		this.clockSkew = clockSkew;
		this.clock = clock;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		Instant now = clock.instant();
		OAuth2TokenValidatorResult result = delegate.validate(jwt);
		if (result.hasErrors()) {
			Instant expiresAt = jwt.getExpiresAt();
			if (expiresAt != null && now.minus(clockSkew).isAfter(expiresAt)) {
				return OAuth2TokenValidatorResult.failure(
						new OAuth2Error(EXPIRED_ERROR_CODE, "Jwt expired at " + expiresAt, null));
			}
			return result;
		}
		Instant issuedAt = jwt.getIssuedAt();
		if (issuedAt == null || issuedAt.isAfter(now.plus(clockSkew))) {
			return OAuth2TokenValidatorResult.failure(
					new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "iat is missing or in the future", null));
		}
		return OAuth2TokenValidatorResult.success();
	}
}
