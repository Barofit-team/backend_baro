package com.barofit.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barofit.IntegrationTest;
import com.barofit.TestTokens;
import com.barofit.common.error.BusinessException;
import com.barofit.identity.api.UserQueryApi;
import com.barofit.identity.api.UserSummary;
import com.barofit.identity.api.event.UserRegistered;
import com.barofit.identity.api.event.UserWithdrawn;
import com.barofit.identity.auth.AuthErrorCode;
import com.barofit.identity.auth.AuthService;
import com.barofit.identity.auth.LoginResult;
import com.barofit.identity.auth.RefreshResult;
import com.barofit.identity.social.SocialAuthorizationCodeClient;
import com.barofit.identity.social.SocialIdTokenVerifier;
import com.barofit.identity.social.SocialIdentity;
import com.barofit.identity.token.RefreshTokenCleanupJob;
import com.barofit.identity.token.RefreshTokenValues;
import com.barofit.identity.user.SocialProvider;
import com.barofit.identity.user.UserAccountService;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** 인증 흐름 통합 테스트. 소셜 ID Token 검증만 mock */
@IntegrationTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class AuthApiTest {

	private static final String KAKAO_SUB = "4242";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	AuthService authService;

	@Autowired
	UserQueryApi userQueryApi;

	@Autowired
	RefreshTokenCleanupJob cleanupJob;

	@Autowired
	UserAccountService userAccountService;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Autowired
	ApplicationEvents events;

	@MockitoBean
	SocialIdTokenVerifier socialIdTokenVerifier;

	@MockitoBean
	SocialAuthorizationCodeClient socialAuthorizationCodeClient;

	@AfterEach
	void cleanUp() {
		jdbcTemplate.execute("TRUNCATE identity.user_profile_goals, identity.user_profiles, identity.refresh_tokens, identity.social_accounts, identity.users");
	}

	@Test
	void 신규_사용자는_가입되고_토큰과_사용자_정보를_받는다() throws Exception {
		givenKakaoUser("카카오유저");

		login()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.accessTokenExpiresAt").isString())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.isOnboarded").value(false))
				.andExpect(jsonPath("$.user.id").isNotEmpty())
				.andExpect(jsonPath("$.user.nickname").value("카카오유저"))
				.andExpect(jsonPath("$.user.profileImageUrl").doesNotExist())
				.andExpect(jsonPath("$.user.role").value("USER"));

		assertThat(count("identity.users")).isEqualTo(1);
		assertThat(count("identity.social_accounts")).isEqualTo(1);
		assertThat(events.stream(UserRegistered.class)).hasSize(1);
	}

	@Test
	void 기존_회원은_같은_사용자로_로그인하고_기기마다_새_세션을_받는다() throws Exception {
		givenKakaoUser("카카오유저");
		Map<String, Object> first = body(login());

		Map<String, Object> second = body(login());

		assertThat(userId(second)).isEqualTo(userId(first));
		assertThat(count("identity.users")).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"select count(distinct session_id) from identity.refresh_tokens", Integer.class)).isEqualTo(2);
		assertThat(events.stream(UserRegistered.class)).hasSize(1);
	}

	@Test
	void 발급한_Access_Token으로_보호된_API를_호출할_수_있다() throws Exception {
		givenKakaoUser("카카오유저");
		String accessToken = (String) body(login()).get("accessToken");

		mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isNoContent());
	}

	@Test
	void 소셜_토큰_검증에_실패하면_AUTH_INVALID_ID_TOKEN() throws Exception {
		given(socialIdTokenVerifier.verify(any(), any()))
				.willThrow(new BusinessException(AuthErrorCode.AUTH_INVALID_ID_TOKEN));

		login()
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_INVALID_ID_TOKEN"));
	}

	@Test
	void 잘못된_provider는_INVALID_INPUT() throws Exception {
		mockMvc.perform(post("/api/v1/auth/social-login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"provider\":\"NAVER\",\"idToken\":\"t\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}

	@Test
	void 같은_신규_사용자의_동시_가입은_한_명으로_합쳐진다() throws Exception {
		givenKakaoUser("동시가입");
		int threads = 8;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<LoginResult>> results = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			Callable<LoginResult> task = () -> {
				start.await();
				return authService.socialLogin(SocialProvider.KAKAO, "id-token");
			};
			results.add(executor.submit(task));
		}
		start.countDown();

		List<UUID> userIds = new ArrayList<>();
		for (Future<LoginResult> result : results) {
			userIds.add(result.get().user().getId());
		}
		executor.shutdown();

		assertThat(userIds).containsOnly(userIds.getFirst());
		assertThat(count("identity.users")).isEqualTo(1);
		assertThat(count("identity.social_accounts")).isEqualTo(1);
	}

	@Test
	void refresh하면_토큰이_회전되고_이전_토큰은_폐기된다() throws Exception {
		givenKakaoUser("카카오유저");
		String oldRefresh = refreshToken(login());

		Map<String, Object> rotated = body(refresh(oldRefresh).andExpect(status().isOk()));

		assertThat(rotated.get("refreshToken")).isNotEqualTo(oldRefresh);
		assertThat(rotated.get("accessToken")).isNotNull();
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from identity.refresh_tokens where revoked_at is null", Integer.class)).isEqualTo(1);
		refresh((String) rotated.get("refreshToken")).andExpect(status().isOk());
	}

	@Test
	void 같은_토큰으로_동시에_refresh하면_하나만_성공하고_세션은_폐기된다() throws Exception {
		givenKakaoUser("카카오유저");
		String refreshToken = refreshToken(login());
		int threads = 2;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<RefreshResult>> results = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			results.add(executor.submit(() -> {
				start.await();
				return authService.refresh(refreshToken);
			}));
		}
		start.countDown();

		List<RefreshResult> outcomes = new ArrayList<>();
		for (Future<RefreshResult> result : results) {
			outcomes.add(result.get());
		}
		executor.shutdown();

		assertThat(outcomes).filteredOn(RefreshResult.Success.class::isInstance).hasSize(1);
		assertThat(outcomes).filteredOn(RefreshResult.Failure.class::isInstance).singleElement()
				.isEqualTo(new RefreshResult.Failure(AuthErrorCode.AUTH_SESSION_REVOKED));
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from identity.refresh_tokens where revoked_at is null", Integer.class)).isZero();
	}

	@Test
	void 폐기된_토큰을_재사용하면_세션_전체가_폐기된다() throws Exception {
		givenKakaoUser("카카오유저");
		String oldRefresh = refreshToken(login());
		String newRefresh = refreshToken(refresh(oldRefresh));

		refresh(oldRefresh)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_SESSION_REVOKED"));

		// 세션 폐기가 커밋됐는지 확인
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from identity.refresh_tokens where revoked_at is null", Integer.class)).isZero();
		refresh(newRefresh).andExpect(jsonPath("$.code").value("AUTH_SESSION_REVOKED"));
	}

	@Test
	void 재사용_탐지는_해당_세션만_폐기한다() throws Exception {
		givenKakaoUser("카카오유저");
		String phone = refreshToken(login());
		String tablet = refreshToken(login());
		refresh(phone);

		refresh(phone).andExpect(jsonPath("$.code").value("AUTH_SESSION_REVOKED"));

		refresh(tablet).andExpect(status().isOk());
	}

	@Test
	void 없는_토큰이면_AUTH_INVALID_REFRESH() throws Exception {
		refresh("unknown")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_INVALID_REFRESH"));
	}

	@Test
	void 슬라이딩_만료가_지난_토큰이면_AUTH_INVALID_REFRESH() throws Exception {
		givenKakaoUser("카카오유저");
		String refreshToken = refreshToken(login());
		jdbcTemplate.update("update identity.refresh_tokens set expires_at = now() - interval '1 second'");

		refresh(refreshToken).andExpect(jsonPath("$.code").value("AUTH_INVALID_REFRESH"));
	}

	@Test
	void 회전해도_절대_만료는_늘어나지_않는다() throws Exception {
		givenKakaoUser("카카오유저");
		String refreshToken = refreshToken(login());
		Instant sessionEnd = Instant.now().plusSeconds(3600);
		jdbcTemplate.update("update identity.refresh_tokens set session_expires_at = ?",
				Timestamp.from(sessionEnd));

		refresh(refreshToken).andExpect(status().isOk());

		Map<String, Object> latest = jdbcTemplate.queryForMap("""
				select expires_at = session_expires_at as capped, session_expires_at
				from identity.refresh_tokens where revoked_at is null""");
		assertThat(latest.get("capped")).isEqualTo(true);
	}

	@Test
	void logout하면_세션이_폐기되고_이후_refresh는_거부된다() throws Exception {
		givenKakaoUser("카카오유저");
		String refreshToken = refreshToken(login());

		logout(refreshToken).andExpect(status().isNoContent());

		refresh(refreshToken).andExpect(jsonPath("$.code").value("AUTH_SESSION_REVOKED"));
	}

	@Test
	void logout은_없는_토큰이나_만료된_토큰에도_204를_응답한다() throws Exception {
		logout("unknown").andExpect(status().isNoContent());

		givenKakaoUser("카카오유저");
		String refreshToken = refreshToken(login());
		jdbcTemplate.update("update identity.refresh_tokens set expires_at = now() - interval '1 second'");
		logout(refreshToken).andExpect(status().isNoContent());
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from identity.refresh_tokens where revoked_at is null", Integer.class)).isZero();
	}

	@Test
	void 공개_경로는_만료된_Access_Token이_붙어도_refresh된다() throws Exception {
		givenKakaoUser("카카오유저");
		String refreshToken = refreshToken(login());
		String expired = TestTokens.accessToken(UUID.randomUUID(), Instant.now().minusSeconds(3600));

		mockMvc.perform(post("/api/v1/auth/refresh").header("Authorization", "Bearer " + expired)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\":\"" + refreshToken + "\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void 탈퇴하면_익명화되고_토큰이_폐기되며_재가입은_신규로_처리된다() throws Exception {
		givenKakaoUser("카카오유저");
		Map<String, Object> loggedIn = body(login());
		String accessToken = (String) loggedIn.get("accessToken");

		withdraw(accessToken).andExpect(status().isNoContent());

		Map<String, Object> user = jdbcTemplate.queryForMap(
				"select nickname, status, withdrawn_at from identity.users where id = ?::uuid", userId(loggedIn));
		assertThat(user.get("nickname")).isNull();
		assertThat(user.get("status")).isEqualTo("WITHDRAWN");
		assertThat(user.get("withdrawn_at")).isNotNull();
		assertThat(count("identity.social_accounts")).isZero();
		assertThat(events.stream(UserWithdrawn.class)).hasSize(1);
		refresh((String) loggedIn.get("refreshToken")).andExpect(jsonPath("$.code").value("AUTH_SESSION_REVOKED"));
		assertThat(userQueryApi.findSummary(UUID.fromString(userId(loggedIn)))).isEmpty();

		Map<String, Object> rejoined = body(login());
		assertThat(userId(rejoined)).isNotEqualTo(userId(loggedIn));
	}

	@Test
	void 이미_탈퇴했어도_탈퇴_요청은_204다() throws Exception {
		givenKakaoUser("카카오유저");
		String accessToken = (String) body(login()).get("accessToken");
		withdraw(accessToken);

		withdraw(accessToken).andExpect(status().isNoContent());

		assertThat(events.stream(UserWithdrawn.class)).hasSize(1);
	}

	@Test
	void 탈퇴는_Access_Token이_필요하다() throws Exception {
		mockMvc.perform(delete("/api/v1/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
	}

	@Test
	void 온보딩을_완료하면_다음_로그인부터_isOnboarded가_true다() throws Exception {
		givenKakaoUser("카카오유저");
		UUID userId = UUID.fromString(userId(body(login())));

		transactionTemplate.executeWithoutResult(
				status -> userAccountService.completeOnboarding(userId));

		login().andExpect(jsonPath("$.isOnboarded").value(true));
	}

	@Test
	void 온보딩_완료는_트랜잭션_밖에서_호출되면_실패한다() {
		assertThatThrownBy(() -> userAccountService.completeOnboarding(UUID.randomUUID()))
				.isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	void 인가_코드로_가입하고_토큰을_받는다() throws Exception {
		givenKakaoCode("code-1", "id-token-1", "nonce-1", "카카오유저");

		codeLogin("KAKAO", "code-1", "http://localhost:8081", "nonce-1")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.isOnboarded").value(false))
				.andExpect(jsonPath("$.user.nickname").value("카카오유저"));

		assertThat(count("identity.users")).isEqualTo(1);
		assertThat(count("identity.social_accounts")).isEqualTo(1);
	}

	@Test
	void 인가_코드_로그인과_ID_Token_로그인은_같은_소셜_계정을_같은_회원으로_본다() throws Exception {
		givenKakaoUser("카카오유저");
		String byIdToken = userId(body(login()));
		givenKakaoCode("code-1", "id-token-1", "nonce-1", "카카오유저");

		String byCode = userId(body(codeLogin("KAKAO", "code-1", "http://localhost:8081", "nonce-1")));

		assertThat(byCode).isEqualTo(byIdToken);
		assertThat(count("identity.users")).isEqualTo(1);
	}

	@Test
	void 허용되지_않은_redirectUri는_코드_교환_없이_400() throws Exception {
		codeLogin("KAKAO", "code-1", "https://evil.example/callback", "nonce-1")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));

		then(socialAuthorizationCodeClient).should(never()).exchange(any(), any(), any());
	}

	@Test
	void 만료되거나_재사용된_인가_코드는_AUTH_INVALID_AUTHORIZATION_CODE() throws Exception {
		given(socialAuthorizationCodeClient.exchange(SocialProvider.KAKAO, "used-code", "http://localhost:8081"))
				.willThrow(new BusinessException(AuthErrorCode.AUTH_INVALID_AUTHORIZATION_CODE));

		codeLogin("KAKAO", "used-code", "http://localhost:8081", "nonce-1")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_INVALID_AUTHORIZATION_CODE"));
		assertThat(count("identity.users")).isZero();
	}

	@Test
	void 인가_코드_로그인에는_nonce가_필요하다() throws Exception {
		mockMvc.perform(post("/api/v1/auth/social-login/code").contentType(MediaType.APPLICATION_JSON)
						.content("{\"provider\":\"KAKAO\",\"code\":\"code-1\",\"redirectUri\":\"http://localhost:8081\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("nonce"));
	}

	@Test
	void 사용자_요약을_조회한다() throws Exception {
		givenKakaoUser("카카오유저");
		UUID userId = UUID.fromString(userId(body(login())));

		assertThat(userQueryApi.findSummary(userId))
				.hasValueSatisfying(summary -> assertThat(summary.nickname()).isEqualTo("카카오유저"));
	}

	@Test
	void 여러_사용자를_한_번에_조회하면_탈퇴했거나_없는_사용자는_빠진다() throws Exception {
		UUID first = loginAs("kakao-sub-1", "첫째");
		UUID second = loginAs("kakao-sub-2", "둘째");
		UUID withdrawn = loginAs("kakao-sub-3", "탈퇴자");
		userAccountService.withdraw(withdrawn);

		Map<UUID, UserSummary> summaries =
				userQueryApi.findSummaries(List.of(first, second, second, withdrawn, UUID.randomUUID()));

		assertThat(summaries).containsOnlyKeys(first, second);
		assertThat(summaries.get(second).nickname()).isEqualTo("둘째");
		assertThat(userQueryApi.findSummaries(List.of())).isEmpty();
	}

	@Test
	void 정리_배치는_절대_만료가_지난_행만_지운다() throws Exception {
		givenKakaoUser("카카오유저");
		String expiredSession = refreshToken(login());
		String activeSession = refreshToken(login());
		refresh(activeSession);
		jdbcTemplate.update("""
				update identity.refresh_tokens set session_expires_at = now() - interval '1 second',
				expires_at = now() - interval '1 second'
				where token_hash = ?""", RefreshTokenValues.hash(expiredSession));

		cleanupJob.deleteSessionExpired();

		// 재사용 탐지용 폐기 행은 남는다
		assertThat(count("identity.refresh_tokens")).isEqualTo(2);
	}

	private void givenKakaoUser(String nickname) {
		given(socialIdTokenVerifier.verify(eq(SocialProvider.KAKAO), any()))
				.willReturn(new SocialIdentity(SocialProvider.KAKAO, KAKAO_SUB, nickname, null));
	}

	private UUID loginAs(String kakaoSub, String nickname) throws Exception {
		given(socialIdTokenVerifier.verify(eq(SocialProvider.KAKAO), any()))
				.willReturn(new SocialIdentity(SocialProvider.KAKAO, kakaoSub, nickname, null));
		return UUID.fromString(userId(body(login())));
	}

	private void givenKakaoCode(String code, String idToken, String nonce, String nickname) {
		given(socialAuthorizationCodeClient.exchange(SocialProvider.KAKAO, code, "http://localhost:8081"))
				.willReturn(idToken);
		given(socialIdTokenVerifier.verify(SocialProvider.KAKAO, idToken, nonce))
				.willReturn(new SocialIdentity(SocialProvider.KAKAO, KAKAO_SUB, nickname, null));
	}

	private ResultActions codeLogin(String provider, String code, String redirectUri, String nonce) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/social-login/code").contentType(MediaType.APPLICATION_JSON)
				.content("{\"provider\":\"%s\",\"code\":\"%s\",\"redirectUri\":\"%s\",\"nonce\":\"%s\"}"
						.formatted(provider, code, redirectUri, nonce)));
	}

	private ResultActions login() throws Exception {
		return mockMvc.perform(post("/api/v1/auth/social-login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"provider\":\"KAKAO\",\"idToken\":\"id-token\"}"));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refreshToken + "\"}"));
	}

	private ResultActions logout(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + refreshToken + "\"}"));
	}

	private ResultActions withdraw(String accessToken) throws Exception {
		return mockMvc.perform(delete("/api/v1/users/me").header("Authorization", "Bearer " + accessToken));
	}

	private static Map<String, Object> body(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$");
	}

	private static String refreshToken(ResultActions result) throws Exception {
		return (String) body(result).get("refreshToken");
	}

	private static String userId(Map<String, Object> loginBody) {
		@SuppressWarnings("unchecked")
		Map<String, Object> user = (Map<String, Object>) loginBody.get("user");
		return (String) user.get("id");
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
	}
}
