package com.barofit.analytics.controller;

import com.barofit.analytics.service.WorkoutCompletionService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkoutCompletionIngestControllerTest {
    @Test
    void internalTokenIsRequiredBeforeStatisticsChange() throws Exception {
        var service = mock(WorkoutCompletionService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new WorkoutCompletionIngestController(service, "expected-token"))
            .setMessageConverters(new JacksonJsonHttpMessageConverter(JsonMapper.builder().build()))
            .build();
        UUID sessionId = UUID.randomUUID();
        String body = """
            {"userId":"%s","routineId":"%s","completedAt":"2026-10-27T09:00:00Z",
             "durationSeconds":120,"totalReps":20}
            """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mvc.perform(put("/internal/workout-completions/" + sessionId)
                .contentType("application/json").content(body))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);

        mvc.perform(put("/internal/workout-completions/" + sessionId)
                .header("X-Internal-Token", "expected-token")
                .contentType("application/json").content(body))
            .andExpect(status().isOk());
        verify(service).recordCompletedWorkout(eq(sessionId), any(UUID.class), any(UUID.class),
            any(), eq(120), eq(20));
    }
}
