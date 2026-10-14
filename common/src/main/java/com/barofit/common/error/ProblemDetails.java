package com.barofit.common.error;

import org.springframework.http.ProblemDetail;

/** code를 더한 RFC 9457 에러 응답 */
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
