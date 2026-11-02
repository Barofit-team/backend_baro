package com.barofit.identity.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3ProfileImageStorageTest {

	private static final UUID USER_ID = UUID.fromString("0199e0a0-0000-7000-8000-000000000001");
	private static final String BASE_URL = "https://barofit-test.s3.ap-northeast-2.amazonaws.com";

	private final S3Client s3 = mock(S3Client.class);

	@Test
	void 버킷에_형식과_캐시_헤더를_붙여_올리고_공개_URL을_돌려준다() {
		var storage = new S3ProfileImageStorage(properties(BASE_URL, "barofit-test", "ap-northeast-2"), s3);

		StoredProfileImage stored = storage.store(USER_ID, ProfileImageFile.from(ProfileImageFileTest.PNG));

		ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
		ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
		verify(s3).putObject(request.capture(), body.capture());
		assertThat(request.getValue().bucket()).isEqualTo("barofit-test");
		assertThat(request.getValue().key()).isEqualTo(stored.key()).startsWith("profile-images/" + USER_ID + "/")
				.endsWith(".png");
		assertThat(request.getValue().contentType()).isEqualTo("image/png");
		assertThat(request.getValue().cacheControl()).isEqualTo(S3ProfileImageStorage.CACHE_CONTROL);
		assertThat(body.getValue().optionalContentLength()).hasValue((long) ProfileImageFileTest.PNG.length);
		assertThat(stored.url()).isEqualTo(BASE_URL + "/" + stored.key());
	}

	@Test
	void 삭제는_같은_버킷의_key를_지운다() {
		var storage = new S3ProfileImageStorage(properties(BASE_URL, "barofit-test", "ap-northeast-2"), s3);

		storage.delete("profile-images/a/b.png");

		ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
		verify(s3).deleteObject(request.capture());
		assertThat(request.getValue().bucket()).isEqualTo("barofit-test");
		assertThat(request.getValue().key()).isEqualTo("profile-images/a/b.png");
	}

	@Test
	void 업로드_실패는_호출한_쪽으로_전달한다() {
		var storage = new S3ProfileImageStorage(properties(BASE_URL, "barofit-test", "ap-northeast-2"), s3);
		org.mockito.Mockito.when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
				.thenThrow(new IllegalStateException("S3 down"));

		// ProfileImageService가 가입 시에는 SNS URL을 유지하고, 사용자 변경 시에는 오류로 응답한다
		assertThatThrownBy(() -> storage.store(USER_ID, ProfileImageFile.from(ProfileImageFileTest.PNG)))
				.hasMessage("S3 down");
	}

	@Test
	void bucket과_region이_없으면_시작하지_않는다() {
		assertThatThrownBy(() -> new S3ProfileImageStorage(properties(BASE_URL, null, null), s3))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new S3ProfileImageStorage(properties(BASE_URL, "barofit-test", " "), s3))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 임시_저장소_주소를_그대로_두면_시작하지_않는다() {
		assertThatThrownBy(() -> new S3ProfileImageStorage(
				properties("https://temp-storage.barofit.invalid", "barofit-test", "ap-northeast-2"), s3))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("public-base-url");
	}

	private static ProfileImageProperties properties(String baseUrl, String bucket, String region) {
		return new ProfileImageProperties(ProfileImageProperties.StorageType.S3, "profile-images", baseUrl,
				new ProfileImageProperties.S3(bucket, region));
	}
}
