package com.barofit.identity.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barofit.common.error.BusinessException;
import com.barofit.identity.profile.ProfileErrorCode;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ProfileImageFileTest {

	static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
	static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};
	static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P'};

	@Test
	void 시그니처로_형식을_판별한다() {
		assertThat(ImageFormat.detect(JPEG)).contains(ImageFormat.JPEG);
		assertThat(ImageFormat.detect(PNG)).contains(ImageFormat.PNG);
		assertThat(ImageFormat.detect(WEBP)).contains(ImageFormat.WEBP);
		assertThat(ImageFormat.detect("GIF89a".getBytes())).isEmpty();
		assertThat(ImageFormat.detect(new byte[] {'R', 'I', 'F', 'F'})).isEmpty();
		assertThat(ImageFormat.detect(null)).isEmpty();
	}

	@Test
	void 업로드_파일은_빈_파일과_허용하지_않는_형식을_거부한다() {
		assertThat(ProfileImageFile.from(PNG).format()).isEqualTo(ImageFormat.PNG);
		assertThatThrownBy(() -> ProfileImageFile.from(new byte[0]))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ProfileErrorCode.PROFILE_IMAGE_INVALID));
		assertThatThrownBy(() -> ProfileImageFile.from("<svg/>".getBytes()))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ProfileErrorCode.PROFILE_IMAGE_INVALID));
	}

	@Test
	void 크기_제한은_5MB까지다() {
		byte[] limit = withJpegHeader((int) ProfileImageFile.MAX_SIZE.toBytes());
		byte[] over = withJpegHeader((int) ProfileImageFile.MAX_SIZE.toBytes() + 1);

		assertThat(ProfileImageFile.from(limit).content()).hasSize(limit.length);
		assertThatThrownBy(() -> ProfileImageFile.from(over))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ProfileErrorCode.PROFILE_IMAGE_TOO_LARGE));
		assertThat(ProfileImageFile.tryFrom(over)).isEmpty();
	}

	static byte[] withJpegHeader(int size) {
		return Arrays.copyOf(JPEG, size);
	}
}
