package com.barofit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@IntegrationTest
class BaroApplicationTests {

	@Autowired
	ApplicationContext context;

	@Test
	void contextLoads() {
	}

	// 소셜 디코더가 Bean이면 바로핏 JWT 검증에 잘못 쓰일 수 있다
	@Test
	void JwtDecoder_Bean은_바로핏_디코더_하나뿐이다() {
		assertThat(context.getBeansOfType(JwtDecoder.class)).containsOnlyKeys("barofitJwtDecoder");
	}

}
