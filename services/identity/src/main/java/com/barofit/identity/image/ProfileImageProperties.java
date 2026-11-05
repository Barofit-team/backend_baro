package com.barofit.identity.image;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 프로필 사진 저장소 설정.
 * storage=temporary는 파일을 보관하지 않고 publicBaseUrl 아래의 URL만 만드는 임시 구현, storage=s3는 S3 버킷에 저장한다.
 * s3이면 publicBaseUrl은 버킷 공개 주소나 CloudFront 주소여야 한다.
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

	/** storage=s3일 때 필수 */
	public record S3(String bucket, String region) {
	}
}
