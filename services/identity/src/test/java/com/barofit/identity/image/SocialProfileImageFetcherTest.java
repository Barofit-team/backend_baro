package com.barofit.identity.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SocialProfileImageFetcherTest {

	private static final String URL = "https://k.kakaocdn.test/dn/a/img_640x640.jpg";

	private final RestClient.Builder builder = RestClient.builder();
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final SocialProfileImageFetcher fetcher = new SocialProfileImageFetcher(builder);

	@Test
	void SNS_사진을_내려받는다() {
		server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(ProfileImageFileTest.JPEG, MediaType.IMAGE_JPEG));

		assertThat(fetcher.fetch(URL)).hasValueSatisfying(file -> {
			assertThat(file.format()).isEqualTo(ImageFormat.JPEG);
			assertThat(file.content()).isEqualTo(ProfileImageFileTest.JPEG);
		});
		server.verify();
	}

	@Test
	void 실패하거나_이미지가_아니면_빈_값이다() {
		server.expect(requestTo(URL)).andRespond(withResourceNotFound());
		assertThat(fetcher.fetch(URL)).isEmpty();
		server.reset();

		server.expect(requestTo(URL)).andRespond(withServerError());
		assertThat(fetcher.fetch(URL)).isEmpty();
		server.reset();

		server.expect(requestTo(URL)).andRespond(withSuccess("<html/>", MediaType.TEXT_HTML));
		assertThat(fetcher.fetch(URL)).isEmpty();
	}

	@Test
	void 크기_제한을_넘으면_빈_값이다() {
		byte[] over = ProfileImageFileTest.withJpegHeader((int) ProfileImageFile.MAX_SIZE.toBytes() + 10);
		server.expect(requestTo(URL)).andRespond(withSuccess(over, MediaType.IMAGE_JPEG));

		assertThat(fetcher.fetch(URL)).isEmpty();
	}

	@Test
	void https가_아닌_URL은_요청하지_않는다() {
		assertThat(fetcher.fetch("http://k.kakaocdn.test/a.jpg")).isEmpty();
		assertThat(fetcher.fetch("file:///etc/passwd")).isEmpty();
		assertThat(fetcher.fetch("not a url")).isEmpty();
		server.verify();
	}
}
