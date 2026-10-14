package com.barofit.security;

import com.barofit.common.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** 인증 실패 응답. 만료와 위조를 다른 code로 구분한다 */
class BarofitAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final HandlerExceptionResolver handlerExceptionResolver;

	BarofitAuthenticationEntryPoint(HandlerExceptionResolver handlerExceptionResolver) {
		this.handlerExceptionResolver = handlerExceptionResolver;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) {
		SecurityErrorCode errorCode = isExpired(authException)
				? SecurityErrorCode.AUTH_TOKEN_EXPIRED
				: SecurityErrorCode.AUTH_INVALID_TOKEN;
		response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		// GlobalExceptionHandler와 같은 형식으로 응답
		handlerExceptionResolver.resolveException(request, response, null, new BusinessException(errorCode));
	}

	private static boolean isExpired(Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof JwtValidationException validation) {
				return validation.getErrors().stream()
						.anyMatch(error -> AccessTokenTimestampValidator.EXPIRED_ERROR_CODE.equals(error.getErrorCode()));
			}
		}
		return false;
	}
}
