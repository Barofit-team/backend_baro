package com.barofit.identity.profile;

import com.barofit.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ProfileErrorCode implements ErrorCode {

	/** 닉네임 형식 오류. 앱: 입력 안내 */
	PROFILE_NICKNAME_INVALID(HttpStatus.BAD_REQUEST, "닉네임은 2~10자의 한글, 영문, 숫자만 사용할 수 있습니다."),
	/** 이미 사용 중인 닉네임. 앱: 다른 닉네임 입력 */
	PROFILE_NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
	/** 온보딩을 이미 마침. 앱: 홈 화면 */
	PROFILE_ALREADY_ONBOARDED(HttpStatus.CONFLICT, "이미 온보딩을 완료했습니다."),
	/** 랜덤 닉네임 생성 실패. 앱: 재시도 또는 직접 입력 */
	PROFILE_NICKNAME_GENERATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "닉네임을 생성하지 못했습니다. 다시 시도해 주세요.");

	private final HttpStatus status;
	private final String message;

	@Override
	public String code() {
		return name();
	}
}
