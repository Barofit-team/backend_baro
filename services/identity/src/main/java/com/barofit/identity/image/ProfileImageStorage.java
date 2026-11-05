package com.barofit.identity.image;

import java.util.UUID;

/** 프로필 사진 저장소. 구현은 barofit.profile-image.storage로 고른다 */
public interface ProfileImageStorage {

	/** 저장하고 앱이 쓸 URL을 돌려준다 */
	StoredProfileImage store(UUID userId, ProfileImageFile file);

	/** 없는 key여도 실패하지 않는다 */
	void delete(String key);
}
