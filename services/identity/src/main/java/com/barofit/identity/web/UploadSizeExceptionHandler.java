package com.barofit.identity.web;

import com.barofit.common.error.ProblemDetails;
import com.barofit.identity.profile.ProfileErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * multipart 크기 초과는 컨트롤러 전에 나므로 공통 핸들러(413 INVALID_INPUT)보다 먼저 잡아 코드를 붙인다.
 * identity에서 multipart를 받는 API는 프로필 사진뿐이다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class UploadSizeExceptionHandler {

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ProblemDetail> handle(MaxUploadSizeExceededException ex) {
		ProfileErrorCode errorCode = ProfileErrorCode.PROFILE_IMAGE_TOO_LARGE;
		return ResponseEntity.status(errorCode.status()).body(ProblemDetails.of(errorCode));
	}
}
