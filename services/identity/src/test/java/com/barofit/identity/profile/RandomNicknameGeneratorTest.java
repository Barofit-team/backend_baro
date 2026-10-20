package com.barofit.identity.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RandomNicknameGeneratorTest {

	@Test
	void 생성한_닉네임은_항상_닉네임_규칙을_만족한다() {
		RandomNicknameGenerator generator = new RandomNicknameGenerator(new SplittableRandom(42));

		IntStream.range(0, 10_000).mapToObj(i -> generator.next())
				.forEach(nickname -> assertThat(NicknamePolicy.isValid(nickname)).as(nickname).isTrue());
	}

	@Test
	void 가장_긴_조합도_최대_길이를_넘지_않는다() {
		int longestAdjective = RandomNicknameGenerator.ADJECTIVES.stream().mapToInt(String::length).max().orElseThrow();
		int longestNoun = RandomNicknameGenerator.NOUNS.stream().mapToInt(String::length).max().orElseThrow();
		int longestNumber = String.valueOf(RandomNicknameGenerator.MAX_NUMBER).length();

		assertThat(longestAdjective + longestNoun + longestNumber).isLessThanOrEqualTo(NicknamePolicy.MAX_LENGTH);
	}
}
