package com.barofit;

import com.barofit.identity.token.TestJwtKeyFiles;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/** 통합 테스트용 JWT 키 */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwtKeyConfiguration {

	public static final TestJwtKeyFiles KEYS = TestJwtKeyFiles.create("test-kid");

	@Bean
	DynamicPropertyRegistrar jwtKeyProperties() {
		return registry -> {
			registry.add("barofit.jwt.private-key-path", () -> KEYS.privateKeyPath().toString());
			registry.add("barofit.jwt.public-jwks-path", () -> KEYS.publicJwksPath().toString());
			registry.add("barofit.jwt.current-kid", KEYS::currentKid);
		};
	}
}
