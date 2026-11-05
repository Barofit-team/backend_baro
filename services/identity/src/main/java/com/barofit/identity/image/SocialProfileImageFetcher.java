package com.barofit.identity.image;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** SNS 프로필 사진(ID Token picture)을 내려받는다. 실패해도 예외 대신 빈 값을 돌려준다 */
@Slf4j
@Component
public class SocialProfileImageFetcher {

	// 가입 응답이 기다리므로 짧게 둔다
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

	private final RestClient restClient;

	@Autowired
	SocialProfileImageFetcher() {
		this(RestClient.builder().requestFactory(requestFactory()));
	}

	// 테스트용 builder 주입
	SocialProfileImageFetcher(RestClient.Builder builder) {
		this.restClient = builder.build();
	}

	public Optional<ProfileImageFile> fetch(String url) {
		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			log.info("Skipped social profile image: malformed URL");
			return Optional.empty();
		}
		if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
			log.info("Skipped social profile image: not an https URL");
			return Optional.empty();
		}
		try {
			byte[] content = restClient.get()
					.uri(uri)
					.accept(MediaType.parseMediaType("image/*"))
					.exchange((request, response) -> {
						if (!response.getStatusCode().is2xxSuccessful()) {
							log.info("Social profile image returned {}", response.getStatusCode().value());
							return null;
						}
						return readLimited(response.getBody());
					});
			Optional<ProfileImageFile> file = ProfileImageFile.tryFrom(content);
			if (content != null && file.isEmpty()) {
				log.info("Skipped social profile image: unsupported format or too large ({} bytes)", content.length);
			}
			return file;
		} catch (RestClientException e) {
			log.warn("Failed to download social profile image from {}", uri.getHost(), e);
			return Optional.empty();
		}
	}

	/** 제한보다 1바이트 더 읽어 초과 여부만 판단한다 */
	private static byte[] readLimited(InputStream body) throws IOException {
		return body.readNBytes((int) ProfileImageFile.MAX_SIZE.toBytes() + 1);
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
		requestFactory.setReadTimeout(READ_TIMEOUT);
		return requestFactory;
	}
}
