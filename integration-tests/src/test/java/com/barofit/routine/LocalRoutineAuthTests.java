package com.barofit.routine;

import com.barofit.IntegrationTest;
import com.barofit.TestTokens;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
class LocalRoutineAuthTests {
    @Autowired MockMvc mvc;

    @Test
    void localProfileAlsoRequiresJwtAndIgnoresLegacyUserHeader() throws Exception {
        mvc.perform(get("/api/v1/routines")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/routines").header("X-Local-User-Id", UUID.randomUUID()))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
        mvc.perform(get("/api/v1/routines").header("Authorization",
                "Bearer " + TestTokens.accessToken(UUID.randomUUID(), Instant.now())))
            .andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.components.securitySchemes.routineAuth.scheme").value("bearer"));
    }
}
