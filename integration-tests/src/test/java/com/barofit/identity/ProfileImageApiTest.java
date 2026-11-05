package com.barofit.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barofit.IntegrationTest;
import com.barofit.TestTokens;
import com.barofit.identity.image.ImageFormat;
import com.barofit.identity.image.ProfileImageFile;
import com.barofit.identity.image.ProfileImageStorage;
import com.barofit.identity.image.SocialProfileImageFetcher;
import com.barofit.identity.social.SocialAuthorizationCodeClient;
import com.barofit.identity.social.SocialIdTokenVerifier;
import com.barofit.identity.social.SocialIdentity;
import com.barofit.identity.user.SocialProvider;
import com.barofit.identity.user.User;
import com.barofit.identity.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 프로필 사진: 가입 시 SNS 사진 가져오기와 변경 API. 저장소는 임시 구현(temporary) */
@IntegrationTest
@AutoConfigureMockMvc
class ProfileImageApiTest {

	private static final String TEMP_BASE_URL = "https://temp-storage.barofit.invalid/";
	private static final String SOCIAL_URL = "https://k.kakaocdn.net/dn/a/img_640x640.jpg";
	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};
	private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	UserRepository userRepository;

	@MockitoBean
	SocialIdTokenVerifier socialIdTokenVerifier;

	@MockitoBean
	SocialAuthorizationCodeClient socialAuthorizationCodeClient;

	@MockitoBean
	SocialProfileImageFetcher socialProfileImageFetcher;

	@MockitoSpyBean
	ProfileImageStorage profileImageStorage;

	@AfterEach
	void cleanUp() {
		jdbcTemplate.execute("TRUNCATE identity.user_profile_discomfort_areas, identity.user_profiles, identity.refresh_tokens, "
				+ "identity.social_accounts, identity.users");
	}

	@Test
	void 처음_가입하면_SNS_사진을_저장소로_옮긴다() throws Exception {
		givenSocialIdentity("1001", SOCIAL_URL);
		given(socialProfileImageFetcher.fetch(SOCIAL_URL))
				.willReturn(Optional.of(new ProfileImageFile(JPEG, ImageFormat.JPEG)));

		String body = socialLogin()
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		String url = JsonPath.read(body, "$.user.profileImageUrl");
		assertThat(url).startsWith(TEMP_BASE_URL + "profile-images/").endsWith(".jpg");
		Map<String, Object> row = imageColumns(UUID.fromString(JsonPath.read(body, "$.user.id")));
		assertThat(row.get("profile_image_source")).isEqualTo("SOCIAL");
		assertThat(row.get("profile_image_key")).isEqualTo(url.substring(TEMP_BASE_URL.length()));
	}

	@Test
	void SNS_사진을_받지_못하면_SNS_URL을_그대로_쓰고_가입은_성공한다() throws Exception {
		givenSocialIdentity("1002", SOCIAL_URL);
		given(socialProfileImageFetcher.fetch(SOCIAL_URL)).willReturn(Optional.empty());

		String body = socialLogin()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.profileImageUrl").value(SOCIAL_URL))
				.andReturn().getResponse().getContentAsString();

		Map<String, Object> row = imageColumns(UUID.fromString(JsonPath.read(body, "$.user.id")));
		assertThat(row.get("profile_image_source")).isEqualTo("SOCIAL");
		assertThat(row.get("profile_image_key")).isNull();
	}

	@Test
	void 이미_가입한_사용자의_로그인과_사진_없는_가입은_사진을_받지_않는다() throws Exception {
		givenSocialIdentity("1003", null);
		socialLogin().andExpect(status().isOk()).andExpect(jsonPath("$.user.profileImageUrl").doesNotExist());

		givenSocialIdentity("1003", SOCIAL_URL);
		socialLogin().andExpect(status().isOk());

		then(socialProfileImageFetcher).should(never()).fetch(any());
	}

	@Test
	void 프로필_사진을_올리면_저장소_URL로_바뀌고_이전_사진은_지운다() throws Exception {
		UUID userId = newUser();

		String first = uploadImage(userId, file(PNG))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(userId.toString()))
				.andReturn().getResponse().getContentAsString();
		String firstKey = imageColumns(userId).get("profile_image_key").toString();
		assertThat(JsonPath.<String>read(first, "$.profileImageUrl")).isEqualTo(TEMP_BASE_URL + firstKey).endsWith(".png");
		assertThat(imageColumns(userId).get("profile_image_source")).isEqualTo("UPLOADED");

		uploadImage(userId, file(JPEG)).andExpect(status().isOk());

		assertThat(imageColumns(userId).get("profile_image_key").toString()).isNotEqualTo(firstKey).endsWith(".jpg");
		then(profileImageStorage).should().delete(firstKey);
	}

	@Test
	void SNS_URL만_있던_사용자가_사진을_올리면_지울_저장소_객체가_없다() throws Exception {
		UUID userId = userRepository.save(User.register("카카오닉", SOCIAL_URL, Instant.now())).getId();

		uploadImage(userId, file(PNG)).andExpect(status().isOk());

		then(profileImageStorage).should(never()).delete(any());
	}

	@Test
	void 형식이_틀리거나_빈_파일이면_400_PROFILE_IMAGE_INVALID() throws Exception {
		UUID userId = newUser();

		uploadImage(userId, file("<svg/>".getBytes()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PROFILE_IMAGE_INVALID"));
		uploadImage(userId, file(new byte[0]))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PROFILE_IMAGE_INVALID"));
		assertThat(imageColumns(userId).get("profile_image_key")).isNull();
	}

	@Test
	void 크기가_5MB를_넘으면_413_PROFILE_IMAGE_TOO_LARGE() throws Exception {
		UUID userId = newUser();
		byte[] tooLarge = Arrays.copyOf(JPEG, (int) ProfileImageFile.MAX_SIZE.toBytes() + 1);

		uploadImage(userId, file(tooLarge))
				.andExpect(status().is(413))
				.andExpect(jsonPath("$.code").value("PROFILE_IMAGE_TOO_LARGE"));
	}

	@Test
	void file_파트가_없으면_400_INVALID_INPUT() throws Exception {
		UUID userId = newUser();

		uploadImage(userId, new MockMultipartFile("image", "a.png", "image/png", PNG))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void 인증이_없으면_401_탈퇴했으면_401_AUTH_USER_WITHDRAWN() throws Exception {
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/users/me/profile-image").file(file(PNG)))
				.andExpect(status().isUnauthorized());

		UUID userId = newUser();
		mockMvc.perform(delete("/api/v1/users/me").header("Authorization", bearer(userId)))
				.andExpect(status().isNoContent());
		uploadImage(userId, file(PNG))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_USER_WITHDRAWN"));
	}

	@Test
	void 탈퇴하면_저장소_사진을_지운다() throws Exception {
		UUID userId = newUser();
		uploadImage(userId, file(PNG)).andExpect(status().isOk());
		String key = imageColumns(userId).get("profile_image_key").toString();

		mockMvc.perform(delete("/api/v1/users/me").header("Authorization", bearer(userId)))
				.andExpect(status().isNoContent());

		then(profileImageStorage).should().delete(key);
		Map<String, Object> row = imageColumns(userId);
		assertThat(row.get("profile_image_key")).isNull();
		assertThat(row.get("profile_image_source")).isNull();
	}

	private void givenSocialIdentity(String subject, String pictureUrl) {
		given(socialAuthorizationCodeClient.exchange(eq(SocialProvider.KAKAO), any(), any())).willReturn("id-token");
		given(socialIdTokenVerifier.verify(eq(SocialProvider.KAKAO), eq("id-token"), any()))
				.willReturn(new SocialIdentity(SocialProvider.KAKAO, subject, "카카오닉", pictureUrl));
	}

	private ResultActions socialLogin() throws Exception {
		return mockMvc.perform(post("/api/v1/auth/social-login/code")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"provider":"KAKAO","code":"c","redirectUri":"http://localhost:8081","nonce":"n"}"""));
	}

	private ResultActions uploadImage(UUID userId, MockMultipartFile file) throws Exception {
		return mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/users/me/profile-image")
				.file(file)
				.header("Authorization", bearer(userId)));
	}

	private static MockMultipartFile file(byte[] content) {
		return new MockMultipartFile("file", "photo", "application/octet-stream", content);
	}

	private UUID newUser() {
		return userRepository.save(User.register(null, null, Instant.now())).getId();
	}

	private Map<String, Object> imageColumns(UUID userId) {
		return jdbcTemplate.queryForMap(
				"select profile_image_key, profile_image_source from identity.users where id = ?", userId);
	}

	private static String bearer(UUID userId) {
		return "Bearer " + TestTokens.accessToken(userId, Instant.now());
	}
}
