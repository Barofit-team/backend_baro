package com.barofit.identity.image;

import java.net.URI;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3 저장소. 객체를 {bucket}/{key}에 올리고 {public-base-url}/{key}를 돌려준다.
 * public-base-url은 버킷 공개 주소나 CloudFront 주소로, 읽기 공개는 버킷 정책·CloudFront가 맡는다(객체 ACL은 쓰지 않는다).
 * 인증은 AWS 기본 자격 증명 체인을 따른다(EC2 인스턴스 프로파일, EKS Pod Identity·IRSA, 환경 변수).
 */
@Slf4j
class S3ProfileImageStorage implements ProfileImageStorage {

	/** key가 업로드마다 새로 만들어지므로(ObjectKeys) 영구 캐시해도 된다 */
	static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

	private final ProfileImageProperties properties;
	private final S3Client s3;

	S3ProfileImageStorage(ProfileImageProperties properties, S3Client s3) {
		validate(properties);
		this.properties = properties;
		this.s3 = s3;
		log.info("프로필 사진 저장소: S3 bucket={}, region={}, public-base-url={}",
				properties.s3().bucket(), properties.s3().region(), properties.publicBaseUrl());
	}

	@Override
	public StoredProfileImage store(UUID userId, ProfileImageFile file) {
		String key = ObjectKeys.newKey(properties.keyPrefix(), userId, file.format());
		s3.putObject(PutObjectRequest.builder()
						.bucket(properties.s3().bucket())
						.key(key)
						.contentType(file.format().contentType())
						.cacheControl(CACHE_CONTROL)
						.build(),
				RequestBody.fromBytes(file.content()));
		return new StoredProfileImage(key, ObjectKeys.publicUrl(properties.publicBaseUrl(), key));
	}

	/** S3 DeleteObject는 없는 key여도 성공한다 */
	@Override
	public void delete(String key) {
		s3.deleteObject(DeleteObjectRequest.builder()
				.bucket(properties.s3().bucket())
				.key(key)
				.build());
	}

	static void validate(ProfileImageProperties properties) {
		if (properties.s3() == null || isBlank(properties.s3().bucket()) || isBlank(properties.s3().region())) {
			throw new IllegalStateException("barofit.profile-image.s3.bucket, region을 설정해야 합니다");
		}
		// 임시 저장소 기본값(.invalid)을 그대로 두면 앱이 열 수 없는 주소를 받는다
		String host = isBlank(properties.publicBaseUrl()) ? null : URI.create(properties.publicBaseUrl()).getHost();
		if (host == null || host.endsWith(".invalid")) {
			throw new IllegalStateException(
					"barofit.profile-image.public-base-url을 S3 버킷 또는 CloudFront 주소로 설정해야 합니다");
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
