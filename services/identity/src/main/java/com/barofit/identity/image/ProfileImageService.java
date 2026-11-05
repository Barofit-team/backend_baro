package com.barofit.identity.image;

import com.barofit.identity.user.ProfileImageSource;
import com.barofit.identity.user.User;
import com.barofit.identity.user.UserAccountService;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 프로필 사진 저장. 네트워크·저장소 호출이 있어 트랜잭션을 열지 않는다 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageService {

	private final ProfileImageStorage storage;
	private final SocialProfileImageFetcher fetcher;
	private final UserAccountService userAccountService;

	/**
	 * 가입 직후 SNS 사진을 바로핏 저장소로 옮긴다.
	 * 내려받기나 저장에 실패하면 SNS URL을 그대로 두고 가입은 계속한다.
	 *
	 * @return 사진이 바뀐 사용자, 바뀌지 않았으면 받은 사용자 그대로
	 */
	public User importSocialImage(User user) {
		String socialUrl = user.getProfileImageUrl();
		if (socialUrl == null || user.getProfileImageSource() != ProfileImageSource.SOCIAL
				|| user.getProfileImageKey() != null) {
			return user;
		}
		Optional<ProfileImageFile> file = fetcher.fetch(socialUrl);
		if (file.isEmpty()) {
			return user;
		}
		try {
			return save(user.getId(), ProfileImageSource.SOCIAL, file.get());
		} catch (RuntimeException e) {
			log.warn("Failed to import social profile image for user {}; keeping social URL", user.getId(), e);
			return user;
		}
	}

	/** 사용자가 올린 사진으로 바꾼다. 형식·크기 위반은 BusinessException */
	public User change(UUID userId, byte[] content) {
		return save(userId, ProfileImageSource.UPLOADED, ProfileImageFile.from(content));
	}

	private User save(UUID userId, ProfileImageSource source, ProfileImageFile file) {
		StoredProfileImage stored = storage.store(userId, file);
		try {
			return userAccountService.changeProfileImage(userId, source, stored.key(), stored.url());
		} catch (RuntimeException e) {
			// DB에 반영하지 못한 객체는 남기지 않는다
			deleteQuietly(stored.key());
			throw e;
		}
	}

	void deleteQuietly(String key) {
		try {
			storage.delete(key);
		} catch (RuntimeException e) {
			log.warn("Failed to delete profile image {}", key, e);
		}
	}
}
