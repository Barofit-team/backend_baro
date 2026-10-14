package com.barofit.identity.token;

import jakarta.validation.constraints.NotBlank;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param privateKeyPath 서명용 개인키. PEM(PKCS#8) 파일
 * @param publicJwksPath 검증용 공개키 목록. JWK Set(JSON) 파일. 키 교체 기간에는 이전 키와 새 키가 함께 들어 있다
 * @param currentKid     현재 서명에 쓰는 키의 kid. 공개키 목록에 있어야 한다
 */
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
