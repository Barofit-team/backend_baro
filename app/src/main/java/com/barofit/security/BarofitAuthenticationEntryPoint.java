package com.barofit.security;

import com.barofit.common.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 인증 실패를 공통 에러 형식(ProblemDetail + code)으로 응답한다.
 * 기본 EntryPoint는 만료와 위조를 모두 invalid_token으로 응답하므로, 앱이 refresh 여부를 판단할 수 있게 둘을 나눈다.
 */
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
		// 컨트롤러 예외와 같은 경로(GlobalExceptionHandler)로 응답 body를 만든다
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
