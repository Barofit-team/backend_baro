package com.barofit.common.error;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드의 형식. 코드 목록은 각 모듈이 enum으로 소유한다(예: identity의 AUTH_*).
 */
public interface ErrorCode {

	/** 응답 body의 code 값. 앱은 이 값으로 동작을 분기한다 */
	String code();

	HttpStatus status();

	/** 기본 detail 메시지 */
	String message();
}
