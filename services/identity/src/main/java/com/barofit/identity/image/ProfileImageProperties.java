package com.barofit.identity.image;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 프로필 사진 저장소 설정.
 * storage=temporary는 S3 연동 전 임시 구현으로, 파일을 보관하지 않고 publicBaseUrl 아래의 URL만 만든다.
 */
@ConfigurationProperties("barofit.profile-image")
public record ProfileImageProperties(
		@DefaultValue("temporary") StorageType storage,
		@DefaultValue("profile-images") String keyPrefix,
		@DefaultValue("https://temp-storage.barofit.invalid") String publicBaseUrl,
		@DefaultValue S3 s3) {

	public enum StorageType {
		TEMPORARY,
		S3
	}

	/** S3 연동 시 사용. AWS 구성이 정해지면 채운다 */
	public record S3(String bucket, String region) {
	}
}
