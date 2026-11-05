package com.barofit.identity.image;

import com.barofit.common.error.BusinessException;
import com.barofit.identity.profile.ProfileErrorCode;
import java.util.Optional;
import org.springframework.util.unit.DataSize;

/** 검증을 통과한 프로필 사진 */
public record ProfileImageFile(byte[] content, ImageFormat format) {

	/** 업로드 크기 제한. multipart 설정(spring.servlet.multipart)과 nginx client_max_body_size도 맞춘다 */
	public static final DataSize MAX_SIZE = DataSize.ofMegabytes(5);

	/** 사용자가 올린 파일. 규칙에 어긋나면 BusinessException */
	public static ProfileImageFile from(byte[] content) {
		if (content == null || content.length == 0) {
			throw new BusinessException(ProfileErrorCode.PROFILE_IMAGE_INVALID);
		}
		if (content.length > MAX_SIZE.toBytes()) {
			throw new BusinessException(ProfileErrorCode.PROFILE_IMAGE_TOO_LARGE);
		}
		ImageFormat format = ImageFormat.detect(content)
				.orElseThrow(() -> new BusinessException(ProfileErrorCode.PROFILE_IMAGE_INVALID));
		return new ProfileImageFile(content, format);
	}

	/** 외부에서 받은 파일. 규칙에 어긋나면 비어 있다 */
	public static Optional<ProfileImageFile> tryFrom(byte[] content) {
		if (content == null || content.length == 0 || content.length > MAX_SIZE.toBytes()) {
			return Optional.empty();
		}
		return ImageFormat.detect(content).map(format -> new ProfileImageFile(content, format));
	}
}
