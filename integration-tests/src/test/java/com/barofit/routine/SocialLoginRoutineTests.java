package com.barofit.routine;

import com.barofit.IntegrationTest;
import com.barofit.TestTokens;
import com.barofit.identity.social.SocialAuthorizationCodeClient;
import com.barofit.identity.social.SocialIdTokenVerifier;
import com.barofit.identity.social.SocialIdentity;
import com.barofit.identity.user.SocialProvider;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 소셜 제공자 통신만 대체하고 가입·토큰 발급·JWT 필터·루틴 DB는 실제로 실행한다. */
@IntegrationTest
@AutoConfigureMockMvc
class SocialLoginRoutineTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean SocialAuthorizationCodeClient codeClient;
    @MockitoBean SocialIdTokenVerifier verifier;
    private UUID userId;

    @AfterEach
    void cleanup() {
        if (userId != null) {
            jdbc.update("DELETE FROM routine.routine WHERE owner_user_id = ?", userId);
            jdbc.update("DELETE FROM identity.refresh_tokens WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM identity.social_accounts WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM identity.users WHERE id = ?", userId);
        }
    }

    @ParameterizedTest
    @EnumSource(SocialProvider.class)
    void socialLoginTokenCanManageOnlyItsOwnRoutineAndRefreshKeepsOwnership(SocialProvider provider) throws Exception {
        given(codeClient.exchange(provider, "test-code", "http://localhost:8081")).willReturn("provider-id-token");
        given(verifier.verify(provider, "provider-id-token", "test-nonce"))
            .willReturn(new SocialIdentity(provider, UUID.randomUUID().toString(), "루틴 테스트", null));
        String login = mvc.perform(post("/api/v1/auth/social-login/code").contentType(APPLICATION_JSON).content("""
            {"provider":"%s","code":"test-code","redirectUri":"http://localhost:8081","nonce":"test-nonce"}
            """.formatted(provider)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = JsonPath.read(login, "$.accessToken");
        String refresh = JsonPath.read(login, "$.refreshToken");
        userId = UUID.fromString(JsonPath.read(login, "$.user.id"));
        String location = mvc.perform(post("/api/v1/routines").header("Authorization", "Bearer " + access)
                .contentType(APPLICATION_JSON).content("""
                {"templateId":"00000000-0000-0000-0000-000000000007"}
                """))
            .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        UUID routineId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM routine.routine WHERE id = ?", UUID.class, routineId))
            .isEqualTo(userId);
        mvc.perform(get(location).header("Authorization", "Bearer " + TestTokens.accessToken(UUID.randomUUID(), Instant.now())))
            .andExpect(status().isNotFound());
        String refreshed = mvc.perform(post("/api/v1/auth/refresh").contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String nextAccess = JsonPath.read(refreshed, "$.accessToken");
        String detail = mvc.perform(get(location).header("Authorization", "Bearer " + nextAccess))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String itemId = JsonPath.read(detail, "$.exercises[0].id");
        mvc.perform(patch(location).header("Authorization", "Bearer " + nextAccess).contentType(APPLICATION_JSON)
                .content("{\"exercises\":[{\"routineExerciseId\":\"" + itemId + "\",\"targetDurationSeconds\":25}]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.exercises[0].sets[0].targetDurationSeconds").value(25));
        mvc.perform(delete(location).header("Authorization", "Bearer " + nextAccess)).andExpect(status().isNoContent());
    }
}
