package com.barofit.identity.token;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtKeyProperties.class)
class JwtKeyConfig {

	@Bean
	JwtKeys jwtKeys(JwtKeyProperties properties) {
		return JwtKeys.load(properties);
	}

	// 서명 키는 identity 안에서만 쓴다
	@Bean
	JwtEncoder barofitJwtEncoder(JwtKeys jwtKeys) {
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtKeys.getSigningKey())));
	}
}
