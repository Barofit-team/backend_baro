package com.barofit.identity.image;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** 허용하는 프로필 사진 형식. Content-Type 헤더 대신 파일 앞부분(시그니처)으로 판별한다 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ImageFormat {

	JPEG("image/jpeg", "jpg"),
	PNG("image/png", "png"),
	WEBP("image/webp", "webp");

	private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
	private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
	private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
	private static final byte[] WEBP_TAG = "WEBP".getBytes(StandardCharsets.US_ASCII);

	private final String contentType;
	private final String extension;

	public static Optional<ImageFormat> detect(byte[] content) {
		if (startsWith(content, 0, JPEG_SIGNATURE)) {
			return Optional.of(JPEG);
		}
		if (startsWith(content, 0, PNG_SIGNATURE)) {
			return Optional.of(PNG);
		}
		// RIFF....WEBP
		if (startsWith(content, 0, RIFF) && startsWith(content, 8, WEBP_TAG)) {
			return Optional.of(WEBP);
		}
		return Optional.empty();
	}

	private static boolean startsWith(byte[] content, int offset, byte[] signature) {
		return content != null && content.length >= offset + signature.length
				&& Arrays.equals(content, offset, offset + signature.length, signature, 0, signature.length);
	}
}
