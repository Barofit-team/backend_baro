package com.barofit.identity.image;

import java.util.UUID;

/** 저장소 객체 key: {prefix}/{userId}/{랜덤}.{확장자}. 같은 사용자도 올릴 때마다 새 key라 캐시가 섞이지 않는다 */
final class ObjectKeys {

	private ObjectKeys() {
	}

	static String newKey(String prefix, UUID userId, ImageFormat format) {
		return prefix + "/" + userId + "/" + UUID.randomUUID() + "." + format.extension();
	}

	static String publicUrl(String baseUrl, String key) {
		return (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + key;
	}
}
