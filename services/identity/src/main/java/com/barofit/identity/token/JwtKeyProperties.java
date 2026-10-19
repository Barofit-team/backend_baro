package com.barofit.identity.token;

import jakarta.validation.constraints.NotBlank;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 개인키(PEM), 공개키 목록(JWK Set), 현재 서명 kid */
@Validated
@ConfigurationProperties("barofit.jwt")
public record JwtKeyProperties(
		@NotBlank String privateKeyPath,
		@NotBlank String publicJwksPath,
		@NotBlank String currentKid) {

	Path privateKeyFile() {
		return Path.of(privateKeyPath);
	}

	Path publicJwksFile() {
		return Path.of(publicJwksPath);
	}
}
