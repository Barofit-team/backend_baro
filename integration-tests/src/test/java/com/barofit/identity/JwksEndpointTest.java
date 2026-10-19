package com.barofit.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barofit.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
class JwksEndpointTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void 공개키만_노출한다() throws Exception {
		mockMvc.perform(get("/.well-known/jwks.json"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.keys[0].kid").value("test-kid"))
				.andExpect(jsonPath("$.keys[0].kty").value("RSA"))
				.andExpect(jsonPath("$.keys[0].n").exists())
				.andExpect(jsonPath("$.keys[0].d").doesNotExist());
	}
}
