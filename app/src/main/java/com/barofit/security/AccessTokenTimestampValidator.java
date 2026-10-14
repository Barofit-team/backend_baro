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

/**
 * exp/nbf 검증(JwtTimestampValidator)을 감싸, 만료일 때는 별도 에러 코드를 담는다.
 * EntryPoint가 예외 메시지 문자열이 아닌 이 코드로 만료와 위조를 구분한다. iat가 미래인 토큰도 거부한다.
 */
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
