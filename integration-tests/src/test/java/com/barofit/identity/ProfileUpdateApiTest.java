package com.barofit.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barofit.IntegrationTest;
import com.barofit.TestTokens;
import com.barofit.identity.user.User;
import com.barofit.identity.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@AutoConfigureMockMvc
class ProfileUpdateApiTest {

	private static final String ONBOARDING_REQUEST = """
			{
			  "nickname": "바로핏",
			  "gender": "FEMALE",
			  "age": 29,
			  "heightCm": 163,
			  "weightKg": 55,
			  "goal": "WEIGHT_LOSS",
			  "experienceLevel": "NOVICE",
			  "weeklyWorkoutDays": 3,
			  "discomfortAreas": ["BACK", "NECK_SHOULDER"],
			  "coachingStyle": "FRIENDLY"
			}""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	UserRepository userRepository;

	@AfterEach
	void cleanUp() {
		jdbcTemplate.execute("TRUNCATE identity.user_profile_discomfort_areas, identity.user_profiles, identity.refresh_tokens, "
				+ "identity.social_accounts, identity.users");
	}

	@Test
	void 보낸_항목만_바뀌고_나머지는_유지된다() throws Exception {
		UUID userId = onboardedUser(ONBOARDING_REQUEST);

		updateProfile(userId, """
				{ "weightKg": 52, "coachingStyle": "SPARTA" }""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nickname").value("바로핏"))
				.andExpect(jsonPath("$.isOnboarded").value(true))
				.andExpect(jsonPath("$.profile.weightKg").value(52))
				.andExpect(jsonPath("$.profile.coachingStyle").value("SPARTA"))
				.andExpect(jsonPath("$.profile.heightCm").value(163))
				.andExpect(jsonPath("$.profile.age").value(29))
				.andExpect(jsonPath("$.profile.goal").value("WEIGHT_LOSS"))
				.andExpect(jsonPath("$.profile.discomfortAreas.length()").value(2));

		mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(userId)))
				.andExpect(jsonPath("$.profile.weightKg").value(52))
				.andExpect(jsonPath("$.profile.coachingStyle").value("SPARTA"))
				.andExpect(jsonPath("$.profile.gender").value("FEMALE"));
	}

	@Test
	void 모든_항목을_한_번에_바꿀_수_있다() throws Exception {
		UUID userId = onboardedUser(ONBOARDING_REQUEST);

		updateProfile(userId, """
				{
				  "nickname": "새닉네임",
				  "gender": "MALE",
				  "age": 35,
				  "heightCm": 180,
				  "weightKg": 80,
				  "goal": "STRENGTH",
				  "experienceLevel": "ADVANCED",
				  "weeklyWorkoutDays": 5,
				  "discomfortAreas": ["LEG_ANKLE"],
				  "coachingStyle": "ANALYTICAL"
				}""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nickname").value("새닉네임"))
				.andExpect(jsonPath("$.profile.gender").value("MALE"))
				.andExpect(jsonPath("$.profile.age").value(35))
				.andExpect(jsonPath("$.profile.heightCm").value(180))
				.andExpect(jsonPath("$.profile.weightKg").value(80))
				.andExpect(jsonPath("$.profile.goal").value("STRENGTH"))
				.andExpect(jsonPath("$.profile.experienceLevel").value("ADVANCED"))
				.andExpect(jsonPath("$.profile.weeklyWorkoutDays").value(5))
				.andExpect(jsonPath("$.profile.discomfortAreas.length()").value(1))
				.andExpect(jsonPath("$.profile.discomfortAreas[0]").value("LEG_ANKLE"))
				.andExpect(jsonPath("$.profile.coachingStyle").value("ANALYTICAL"));
		assertThat(jdbcTemplate.queryForObject(
				"select birth_year from identity.user_profiles where user_id = ?", Integer.class, userId))
				.isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")).getYear() - 35);
		assertThat(count("identity.user_profile_discomfort_areas")).isEqualTo(1);
	}

	@Test
	void 불편_부위는_빈_배열이면_비우고_null이면_유지한다() throws Exception {
		UUID userId = onboardedUser(ONBOARDING_REQUEST);

		updateProfile(userId, """
				{ "discomfortAreas": null, "age": 30 }""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.profile.discomfortAreas.length()").value(2));
		updateProfile(userId, """
				{ "discomfortAreas": [] }""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.profile.discomfortAreas.length()").value(0));
		assertThat(count("identity.user_profile_discomfort_areas")).isZero();
	}

	@Test
	void 닉네임을_바꾸면_이전_닉네임은_다른_사용자가_쓸_수_있다() throws Exception {
		UUID userId = onboardedUser(ONBOARDING_REQUEST);

		updateProfile(userId, """
				{ "nickname": "바뀐닉네임" }""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nickname").value("바뀐닉네임"));

		onboard(newUser(), ONBOARDING_REQUEST).andExpect(status().isCreated());
	}

	@Test
	void 자기_닉네임의_대소문자만_바꾸는_것은_허용한다() throws Exception {
		UUID userId = onboardedUser(ONBOARDING_REQUEST.replace("바로핏", "BaroFit"));

		updateProfile(userId, """
				{ "nickname": "barofit" }""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nickname").value("barofit"));
		updateProfile(userId, """
				{ "nickname": "barofit" }""")
				.andExpect(status().isOk());
	}

	@Test
	void 다른_사용자의_닉네임으로는_바꿀_수_없다() throws Exception {
		onboardedUser(ONBOARDING_REQUEST.replace("바로핏", "BaroFit"));
		UUID userId = onboardedUser(ONBOARDING_REQUEST.replace("바로핏", "다른사람"));

		updateProfile(userId, """
				{ "nickname": "barofit", "weightKg": 60 }""")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROFILE_NICKNAME_DUPLICATED"));

		// 실패하면 다른 항목도 바뀌지 않는다
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(userId)))
				.andExpect(jsonPath("$.nickname").value("다른사람"))
				.andExpect(jsonPath("$.profile.weightKg").value(55));
	}

	@Test
	void 닉네임_형식이_틀리면_400_PROFILE_NICKNAME_INVALID() throws Exception {
		UUID userId = onboardedUser(ONBOARDING_REQUEST);

		updateProfile(userId, """
				{ "nickname": "바로 핏!" }""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PROFILE_NICKNAME_INVALID"));
	}

	@Test
	void 범위_오류나_소수점은_400_INVALID_INPUT() throws Exception {
		UUID userId = onboardedUser(ONBOARDING_REQUEST);

		updateProfile(userId, """
				{ "age": 13, "weeklyWorkoutDays": 7, "heightCm": 163.5, "weightKg": 301 }""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.errors.length()").value(4));
		updateProfile(userId, """
				{ "coachingStyle": "GENTLE" }""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		updateProfile(userId, """
				{ "discomfortAreas": ["KNEE"] }""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void 바꿀_항목이_없으면_400_INVALID_INPUT() throws Exception {
		UUID userId = onboardedUser(ONBOARDING_REQUEST);

		updateProfile(userId, "{}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		updateProfile(userId, """
				{ "nickname": null, "gender": null }""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void 온보딩_전이면_409_PROFILE_NOT_ONBOARDED() throws Exception {
		UUID userId = newUser();

		updateProfile(userId, """
				{ "weightKg": 52 }""")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROFILE_NOT_ONBOARDED"));
		assertThat(count("identity.user_profiles")).isZero();
	}

	@Test
	void 탈퇴한_사용자는_401_AUTH_USER_WITHDRAWN() throws Exception {
		UUID userId = onboardedUser(ONBOARDING_REQUEST);
		mockMvc.perform(delete("/api/v1/users/me").header("Authorization", bearer(userId)))
				.andExpect(status().isNoContent());

		updateProfile(userId, """
				{ "weightKg": 52 }""")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_USER_WITHDRAWN"));
	}

	@Test
	void 인증_없이_호출하면_401() throws Exception {
		mockMvc.perform(patch("/api/v1/users/me/profile")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{ \"weightKg\": 52 }"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
	}

	private UUID newUser() {
		return userRepository.save(User.register(null, null, Instant.now())).getId();
	}

	private UUID onboardedUser(String onboardingRequest) throws Exception {
		UUID userId = newUser();
		onboard(userId, onboardingRequest).andExpect(status().isCreated());
		return userId;
	}

	private ResultActions onboard(UUID userId, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/users/me/onboarding")
				.header("Authorization", bearer(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private ResultActions updateProfile(UUID userId, String body) throws Exception {
		return mockMvc.perform(patch("/api/v1/users/me/profile")
				.header("Authorization", bearer(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private static String bearer(UUID userId) {
		return "Bearer " + TestTokens.accessToken(userId, Instant.now());
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
	}
}
