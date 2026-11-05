package com.barofit.identity.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class NicknamePolicyTest {

	@ParameterizedTest
	@ValueSource(strings = {"바로", "바로핏Fit2026", "abc", "ABC123", "가나다라마바사아자차"})
	void 한글_영문_숫자_2에서_10자는_허용한다(String nickname) {
		assertThat(NicknamePolicy.isValid(nickname)).isTrue();
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", "a", "가나다라마바사아자차카", "바로 핏", "바로_핏", "ㅂㄹ", "바로!", "😀😀"})
	void 길이나_문자가_맞지_않으면_거부한다(String nickname) {
		assertThat(NicknamePolicy.isValid(nickname)).isFalse();
	}
}
