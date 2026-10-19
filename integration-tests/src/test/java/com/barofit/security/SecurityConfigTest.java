package com.barofit.security;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barofit.IntegrationTest;
import com.barofit.TestJwtKeyConfiguration;
import com.barofit.TestTokens;
import com.barofit.identity.token.TestJwtKeyFiles;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@IntegrationTest
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(SecurityConfigTest.SecuredEndpointConfig.class)
class SecurityConfigTest {

	private static final String SECURED = "/test/secured";

	@Autowired
	MockMvc mockMvc;

	@Test
	void 유효한_토큰이면_sub와_role로_인증된다() throws Exception {
		UUID userId = UUID.randomUUID();

		mockMvc.perform(get(SECURED).header("Authorization", bearer(TestTokens.accessToken(userId, Instant.now()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sub").value(userId.toString()))
				.andExpect(jsonPath("$.authorities", hasItem("ROLE_USER")));
	}

	@Test
	void 토큰_없이_보호된_API에_접근하면_AUTH_INVALID_TOKEN() throws Exception {
		mockMvc.perform(get(SECURED))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"))
				.andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
	}

	@Test
	void 만료된_토큰이면_AUTH_TOKEN_EXPIRED() throws Exception {
		Instant issuedAt = Instant.now().minus(Duration.ofMinutes(17));

		mockMvc.perform(get(SECURED).header("Authorization", bearer(TestTokens.accessToken(UUID.randomUUID(), issuedAt))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"));
	}

	@Test
	void 만료_후_60초_이내는_허용한다() throws Exception {
		Instant issuedAt = Instant.now().minus(Duration.ofMinutes(15)).minusSeconds(30);

		mockMvc.perform(get(SECURED).header("Authorization", bearer(TestTokens.accessToken(UUID.randomUUID(), issuedAt))))
				.andExpect(status().isOk());
	}

	@Test
	void iss가_다르면_AUTH_INVALID_TOKEN() throws Exception {
		String token = TestTokens.accessToken(TestJwtKeyConfiguration.KEYS.keyPair(), UUID.randomUUID(), Instant.now(),
				claims -> claims.issuer("someone-else"));

		expectInvalid(token);
	}

	@Test
	void aud가_다르면_AUTH_INVALID_TOKEN() throws Exception {
		String token = TestTokens.accessToken(TestJwtKeyConfiguration.KEYS.keyPair(), UUID.randomUUID(), Instant.now(),
				claims -> claims.audience(List.of("other-api")));

		expectInvalid(token);
	}

	@Test
	void iat가_미래이면_AUTH_INVALID_TOKEN() throws Exception {
		String token = TestTokens.accessToken(TestJwtKeyConfiguration.KEYS.keyPair(), UUID.randomUUID(), Instant.now(),
				claims -> claims.issuedAt(Instant.now().plus(Duration.ofMinutes(5))));

		expectInvalid(token);
	}

	@Test
	void 다른_키로_서명한_토큰이면_AUTH_INVALID_TOKEN() throws Exception {
		String token = TestTokens.accessToken(TestJwtKeyFiles.generateKeyPair(), UUID.randomUUID(), Instant.now(),
				claims -> {
				});

		expectInvalid(token);
	}

	@Test
	void 형식이_잘못된_토큰이면_AUTH_INVALID_TOKEN() throws Exception {
		expectInvalid("not-a-jwt");
	}

	@Test
	void 공개_경로는_만료된_Bearer_헤더가_붙어도_통과한다() throws Exception {
		String expired = TestTokens.accessToken(UUID.randomUUID(), Instant.now().minus(Duration.ofHours(1)));

		mockMvc.perform(get("/.well-known/jwks.json").header("Authorization", bearer(expired)))
				.andExpect(status().isOk());
	}

	@Test
	void 헬스체크는_인증_없이_접근한다() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk());
	}

	private void expectInvalid(String token) throws Exception {
		mockMvc.perform(get(SECURED).header("Authorization", bearer(token)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SecuredEndpointConfig {

		@Bean
		SecuredController securedController() {
			return new SecuredController();
		}
	}

	@RestController
	static class SecuredController {

		@GetMapping(SECURED)
		Map<String, Object> secured(@AuthenticationPrincipal Jwt jwt) {
			var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
					.map(GrantedAuthority::getAuthority)
					.toList();
			return Map.of("sub", jwt.getSubject(), "authorities", authorities);
		}
	}
}
