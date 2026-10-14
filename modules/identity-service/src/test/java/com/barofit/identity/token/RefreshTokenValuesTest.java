package com.barofit.identity.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class RefreshTokenValuesTest {

	@Test
	void 원문은_256bit_랜덤_값이다() {
		String token = RefreshTokenValues.generate();

		assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
		assertThat(RefreshTokenValues.generate()).isNotEqualTo(token);
	}

	@Test
	void 해시는_SHA_256_hex_64자다() {
		String hash = RefreshTokenValues.hash("token");

		assertThat(hash).hasSize(64).isEqualTo(RefreshTokenValues.hash("token"));
	}
}
