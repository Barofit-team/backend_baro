package com.barofit.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barofit.IntegrationTest;
import com.barofit.TestTokens;
import com.barofit.common.error.BusinessException;
import com.barofit.identity.profile.CoachingStyle;
import com.barofit.identity.profile.DiscomfortArea;
import com.barofit.identity.profile.ExerciseGoal;
import com.barofit.identity.profile.ExperienceLevel;
import com.barofit.identity.profile.Gender;
import com.barofit.identity.profile.NicknamePolicy;
import com.barofit.identity.profile.OnboardingCommand;
import com.barofit.identity.profile.ProfileErrorCode;
import com.barofit.identity.profile.ProfileService;
import com.barofit.identity.user.User;
import com.barofit.identity.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class OnboardingApiTest {

	private static final String FULL_REQUEST = """
			{
			  "nickname": "바로핏",
			  "gender": "FEMALE",
			  "age": 29,
			  "heightCm": 163.5,
			  "weightKg": 55.2,
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

	@Autowired
	ProfileService profileService;

	@AfterEach
	void cleanUp() {
		jdbcTemplate.execute("TRUNCATE identity.user_profile_discomfort_areas, identity.user_profiles, identity.refresh_tokens, "
				+ "identity.social_accounts, identity.users");
	}

	@Test
	void 온보딩_전에는_profile이_null이다() throws Exception {
		UUID userId = newUser("카카오닉");

		mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(userId.toString()))
				.andExpect(jsonPath("$.nickname").value("카카오닉"))
				.andExpect(jsonPath("$.isOnboarded").value(false))
				.andExpect(jsonPath("$.profile").doesNotExist());
	}

	@Test
	void 온보딩하면_닉네임과_프로필이_저장되고_onboarded가_된다() throws Exception {
		UUID userId = newUser("카카오닉");

		onboard(userId, FULL_REQUEST)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nickname").value("바로핏"))
				.andExpect(jsonPath("$.isOnboarded").value(true))
				.andExpect(jsonPath("$.profile.gender").value("FEMALE"))
				.andExpect(jsonPath("$.profile.age").value(29))
				.andExpect(jsonPath("$.profile.heightCm").value(163.5))
				.andExpect(jsonPath("$.profile.goal").value("WEIGHT_LOSS"))
				.andExpect(jsonPath("$.profile.discomfortAreas[0]").value("NECK_SHOULDER"))
				.andExpect(jsonPath("$.profile.discomfortAreas[1]").value("BACK"));

		mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(userId)))
				.andExpect(jsonPath("$.isOnboarded").value(true))
				.andExpect(jsonPath("$.profile.age").value(29))
				.andExpect(jsonPath("$.profile.experienceLevel").value("NOVICE"))
				.andExpect(jsonPath("$.profile.weeklyWorkoutDays").value(3))
				.andExpect(jsonPath("$.profile.discomfortAreas.length()").value(2))
				.andExpect(jsonPath("$.profile.coachingStyle").value("FRIENDLY"));
		assertThat(jdbcTemplate.queryForObject(
				"select onboarded from identity.users where id = ?", Boolean.class, userId)).isTrue();
		assertThat(jdbcTemplate.queryForObject(
				"select birth_year from identity.user_profiles where user_id = ?", Integer.class, userId))
				.isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")).getYear() - 29);
		assertThat(count("identity.user_profile_discomfort_areas")).isEqualTo(2);
	}

	@Test
	void 불편_부위가_없으면_빈_배열로_보낸다() throws Exception {
		UUID userId = newUser(null);

		onboard(userId, FULL_REQUEST.replace("[\"BACK\", \"NECK_SHOULDER\"]", "[]"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.profile.discomfortAreas.length()").value(0));
		assertThat(count("identity.user_profile_discomfort_areas")).isZero();
	}

	@Test
	void 이미_온보딩했으면_409_PROFILE_ALREADY_ONBOARDED() throws Exception {
		UUID userId = newUser(null);
		onboard(userId, FULL_REQUEST).andExpect(status().isCreated());

		onboard(userId, FULL_REQUEST.replace("바로핏", "다른닉네임"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROFILE_ALREADY_ONBOARDED"));
	}

	@Test
	void 다른_사용자가_확정한_닉네임은_대소문자와_상관없이_쓸_수_없다() throws Exception {
		onboard(newUser(null), FULL_REQUEST.replace("바로핏", "BaroFit")).andExpect(status().isCreated());
		UUID other = newUser(null);

		onboard(other, FULL_REQUEST.replace("바로핏", "barofit"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROFILE_NICKNAME_DUPLICATED"));
		availability(other, "BAROFIT")
				.andExpect(jsonPath("$.available").value(false))
				.andExpect(jsonPath("$.reason").value("DUPLICATED"));
	}

	@Test
	void 온보딩_전_사용자끼리의_SNS_닉네임_중복은_막지_않는다() throws Exception {
		UUID userId = newUser("바로핏");
		newUser("바로핏");

		availability(userId, "바로핏")
				.andExpect(jsonPath("$.available").value(true))
				.andExpect(jsonPath("$.reason").doesNotExist());
		onboard(userId, FULL_REQUEST).andExpect(status().isCreated());
	}

	@Test
	void 닉네임_형식이_틀리면_400_PROFILE_NICKNAME_INVALID() throws Exception {
		UUID userId = newUser(null);

		onboard(userId, FULL_REQUEST.replace("바로핏", "바로 핏!"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PROFILE_NICKNAME_INVALID"));
		availability(userId, "a")
				.andExpect(jsonPath("$.available").value(false))
				.andExpect(jsonPath("$.reason").value("INVALID_FORMAT"));
	}

	@Test
	void 필수값_누락이나_범위_오류는_400_INVALID_INPUT() throws Exception {
		UUID userId = newUser(null);

		onboard(userId, FULL_REQUEST.replace("\"age\": 29", "\"age\": 13")
				.replace("\"weeklyWorkoutDays\": 3", "\"weeklyWorkoutDays\": 7")
				.replace("163.5", "163.55")
				.replace("\"discomfortAreas\": [\"BACK\", \"NECK_SHOULDER\"],", ""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"))
				.andExpect(jsonPath("$.errors.length()").value(4));
		onboard(userId, FULL_REQUEST.replace("\"coachingStyle\": \"FRIENDLY\"", "\"coachingStyle\": \"GENTLE\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		onboard(userId, FULL_REQUEST.replace("\"goal\": \"WEIGHT_LOSS\"", "\"goal\": [\"WEIGHT_LOSS\"]"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		onboard(userId, FULL_REQUEST.replace("\"BACK\"", "\"KNEE\""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
		assertThat(count("identity.user_profiles")).isZero();
	}

	@Test
	void 같은_닉네임으로_동시에_온보딩하면_한_명만_성공한다() throws Exception {
		List<UUID> userIds = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			userIds.add(newUser(null));
		}

		List<Throwable> failures = runConcurrently(userIds.stream()
				.<Callable<Object>>map(userId -> () -> profileService.onboard(userId, command("동시닉네임")))
				.toList());

		assertThat(failures).hasSize(5).allSatisfy(failure -> assertThat(failure)
				.isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
						.isEqualTo(ProfileErrorCode.PROFILE_NICKNAME_DUPLICATED)));
		assertThat(count("identity.user_profiles")).isEqualTo(1);
	}

	@Test
	void 같은_사용자의_동시_온보딩은_한_번만_반영된다() throws Exception {
		UUID userId = newUser(null);

		List<Throwable> failures = runConcurrently(List.of(
				() -> profileService.onboard(userId, command("첫번째")),
				() -> profileService.onboard(userId, command("두번째")),
				() -> profileService.onboard(userId, command("세번째"))));

		assertThat(failures).hasSize(2).allSatisfy(failure -> assertThat(failure)
				.isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
						.isEqualTo(ProfileErrorCode.PROFILE_ALREADY_ONBOARDED)));
		assertThat(count("identity.user_profiles")).isEqualTo(1);
	}

	@Test
	void 랜덤_닉네임은_규칙에_맞고_사용_가능하다() throws Exception {
		UUID userId = newUser(null);

		String body = mockMvc.perform(get("/api/v1/users/nicknames/random").header("Authorization", bearer(userId)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String nickname = JsonPath.read(body, "$.nickname");

		assertThat(NicknamePolicy.isValid(nickname)).isTrue();
		onboard(userId, FULL_REQUEST.replace("바로핏", nickname)).andExpect(status().isCreated());
	}

	@Test
	void 탈퇴하면_프로필이_삭제되고_닉네임이_반납된다() throws Exception {
		UUID userId = newUser(null);
		onboard(userId, FULL_REQUEST).andExpect(status().isCreated());

		mockMvc.perform(delete("/api/v1/users/me").header("Authorization", bearer(userId)))
				.andExpect(status().isNoContent());

		assertThat(count("identity.user_profiles")).isZero();
		assertThat(count("identity.user_profile_discomfort_areas")).isZero();
		onboard(newUser(null), FULL_REQUEST).andExpect(status().isCreated());
		mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(userId)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_USER_WITHDRAWN"));
	}

	@Test
	void 인증_없이_호출하면_401() throws Exception {
		mockMvc.perform(get("/api/v1/users/nicknames/random"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
		mockMvc.perform(post("/api/v1/users/me/onboarding").contentType(MediaType.APPLICATION_JSON).content(FULL_REQUEST))
				.andExpect(status().isUnauthorized());
	}

	private UUID newUser(String nickname) {
		return userRepository.save(User.register(nickname, null, Instant.now())).getId();
	}

	private ResultActions onboard(UUID userId, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/users/me/onboarding")
				.header("Authorization", bearer(userId))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private ResultActions availability(UUID userId, String nickname) throws Exception {
		return mockMvc.perform(get("/api/v1/users/nicknames/availability")
				.header("Authorization", bearer(userId))
				.param("nickname", nickname));
	}

	private static String bearer(UUID userId) {
		return "Bearer " + TestTokens.accessToken(userId, Instant.now());
	}

	private static OnboardingCommand command(String nickname) {
		return new OnboardingCommand(nickname, Gender.MALE, 31, new BigDecimal("175.0"), new BigDecimal("70.0"),
				ExerciseGoal.STRENGTH, ExperienceLevel.BEGINNER, 4, Set.of(DiscomfortArea.ARM_WRIST),
				CoachingStyle.SPARTA);
	}

	/** 동시에 실행하고 실패한 예외만 모은다 */
	private static List<Throwable> runConcurrently(List<Callable<Object>> tasks) throws InterruptedException {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Object>> futures = new ArrayList<>();
		for (Callable<Object> task : tasks) {
			futures.add(executor.submit(() -> {
				start.await();
				return task.call();
			}));
		}
		start.countDown();
		List<Throwable> failures = new ArrayList<>();
		for (Future<Object> future : futures) {
			try {
				future.get();
			} catch (ExecutionException e) {
				failures.add(e.getCause());
			}
		}
		executor.shutdown();
		return failures;
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
	}
}
