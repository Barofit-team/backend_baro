package com.barofit.common.error;

import org.springframework.http.HttpStatus;

/** 에러 코드 형식. 목록은 각 모듈 enum이 가진다 */
public interface ErrorCode {

	/** 응답 code 값 */
	String code();

	HttpStatus status();

	/** 기본 detail 메시지 */
	String message();
}
