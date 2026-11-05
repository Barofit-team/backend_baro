package com.barofit.identity.image;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * S3 연동 전 임시 저장소. 파일을 보관하지 않고 key와 임시 URL만 만든다.
 * 돌려준 URL은 실제로 열리지 않는다.
 */
@Slf4j
class TemporaryProfileImageStorage implements ProfileImageStorage {

	private final ProfileImageProperties properties;

	TemporaryProfileImageStorage(ProfileImageProperties properties) {
		this.properties = properties;
		log.warn("프로필 사진 저장소가 임시 구현(temporary)입니다. 사진을 보관하지 않고 {} 아래의 임시 URL만 돌려줍니다",
				properties.publicBaseUrl());
	}

	@Override
	public StoredProfileImage store(UUID userId, ProfileImageFile file) {
		String key = ObjectKeys.newKey(properties.keyPrefix(), userId, file.format());
		log.info("[temporary] profile image not uploaded: key={}, {} bytes", key, file.content().length);
		return new StoredProfileImage(key, ObjectKeys.publicUrl(properties.publicBaseUrl(), key));
	}

	@Override
	public void delete(String key) {
		log.info("[temporary] profile image not deleted: key={}", key);
	}
}
