package com.barofit.common.error;

import org.springframework.http.ProblemDetail;

/**
 * RFC 9457 Problem Details에 code 속성을 더한 공통 에러 응답을 만든다.
 * 컨트롤러 밖(예: 보안 필터의 EntryPoint)에서도 같은 형식으로 응답할 때 사용한다.
 */
public final class ProblemDetails {

	public static final String CODE = "code";
	public static final String ERRORS = "errors";

	private ProblemDetails() {
	}

	public static ProblemDetail of(ErrorCode errorCode) {
		return of(errorCode, errorCode.message());
	}

	public static ProblemDetail of(ErrorCode errorCode, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
		problem.setProperty(CODE, errorCode.code());
		return problem;
	}
}
