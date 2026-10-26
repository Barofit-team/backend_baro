package com.barofit.identity.image;

import java.util.UUID;

/**
 * S3 저장소 자리. AWS 구성(버킷, 리전, 인증, CloudFront 여부)이 정해지면 구현한다.
 *
 * <p>구현할 내용:
 * <ul>
 *   <li>store: PutObject(bucket, key, content, contentType=format.contentType()) 후 publicBaseUrl(CloudFront 등) + key 반환</li>
 *   <li>delete: DeleteObject(bucket, key). 없는 key여도 성공</li>
 *   <li>build.gradle에 AWS SDK(software.amazon.awssdk:s3) 추가</li>
 * </ul>
 */
class S3ProfileImageStorage implements ProfileImageStorage {

	private final ProfileImageProperties properties;

	S3ProfileImageStorage(ProfileImageProperties properties) {
		if (properties.s3() == null || isBlank(properties.s3().bucket()) || isBlank(properties.s3().region())) {
			throw new IllegalStateException("barofit.profile-image.s3.bucket, region을 설정해야 합니다");
		}
		this.properties = properties;
	}

	@Override
	public StoredProfileImage store(UUID userId, ProfileImageFile file) {
		throw new UnsupportedOperationException("S3 프로필 사진 업로드는 아직 연동하지 않았습니다 (bucket="
				+ properties.s3().bucket() + ")");
	}

	@Override
	public void delete(String key) {
		throw new UnsupportedOperationException("S3 프로필 사진 삭제는 아직 연동하지 않았습니다");
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
