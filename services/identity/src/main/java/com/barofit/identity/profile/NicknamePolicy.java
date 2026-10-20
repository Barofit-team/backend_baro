package com.barofit.identity.profile;

import java.util.regex.Pattern;

/** 닉네임 형식: 2~10자, 한글(완성형)·영문·숫자. 중복은 대소문자를 무시한다 */
public final class NicknamePolicy {

	public static final int MIN_LENGTH = 2;
	public static final int MAX_LENGTH = 10;

	private static final Pattern PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9]{" + MIN_LENGTH + "," + MAX_LENGTH + "}$");

	private NicknamePolicy() {
	}

	public static boolean isValid(String nickname) {
		return nickname != null && PATTERN.matcher(nickname).matches();
	}
}
