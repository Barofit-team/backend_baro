package com.barofit.identity.profile;

import java.security.SecureRandom;
import java.util.List;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/** 형용사 + 운동·동물 명사 + 숫자. 단어는 3자 이하라 최대 10자로 NicknamePolicy를 항상 만족한다 */
@Component
public class RandomNicknameGenerator {

	static final List<String> ADJECTIVES = List.of(
			"날쌘", "힘찬", "씩씩한", "단단한", "가벼운", "튼튼한", "활기찬", "용감한", "성실한", "유연한",
			"빠른", "꾸준한", "당당한", "상쾌한", "즐거운", "멋진", "든든한", "신나는", "밝은", "굳센");

	static final List<String> NOUNS = List.of(
			"스쿼트", "플랭크", "푸시업", "런지", "버피", "덤벨", "바벨", "근육", "호랑이", "다람쥐",
			"치타", "펭귄", "독수리", "곰", "사자", "고래", "토끼", "거북이", "판다", "여우");

	static final int MAX_NUMBER = 9999;

	private final RandomGenerator random;

	public RandomNicknameGenerator() {
		this(new SecureRandom());
	}

	RandomNicknameGenerator(RandomGenerator random) {
		this.random = random;
	}

	public String next() {
		return ADJECTIVES.get(random.nextInt(ADJECTIVES.size()))
				+ NOUNS.get(random.nextInt(NOUNS.size()))
				+ (random.nextInt(MAX_NUMBER) + 1);
	}
}
