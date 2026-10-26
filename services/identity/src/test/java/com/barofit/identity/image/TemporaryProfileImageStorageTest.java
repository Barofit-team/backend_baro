package com.barofit.identity.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TemporaryProfileImageStorageTest {

	private static final UUID USER_ID = UUID.fromString("0199e0a0-0000-7000-8000-000000000001");

	@Test
	void 사용자별_새_key와_임시_URL을_만든다() {
		var storage = new TemporaryProfileImageStorage(properties("https://temp.test/"));
		ProfileImageFile file = ProfileImageFile.from(ProfileImageFileTest.PNG);

		StoredProfileImage first = storage.store(USER_ID, file);
		StoredProfileImage second = storage.store(USER_ID, file);

		assertThat(first.key()).startsWith("profile-images/" + USER_ID + "/").endsWith(".png");
		assertThat(first.url()).isEqualTo("https://temp.test/" + first.key());
		assertThat(second.key()).isNotEqualTo(first.key());
	}

	@Test
	void S3_저장소는_bucket과_region이_없으면_시작하지_않는다() {
		var properties = new ProfileImageProperties(ProfileImageProperties.StorageType.S3, "profile-images",
				"https://cdn.test", new ProfileImageProperties.S3(null, null));

		assertThatThrownBy(() -> new S3ProfileImageStorage(properties)).isInstanceOf(IllegalStateException.class);
	}

	private static ProfileImageProperties properties(String baseUrl) {
		return new ProfileImageProperties(ProfileImageProperties.StorageType.TEMPORARY, "profile-images", baseUrl,
				new ProfileImageProperties.S3(null, null));
	}
}
