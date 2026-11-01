package com.barofit.workout;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CoachingProfileClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json;
    private final URI profileUri;

    public CoachingProfileClient(ObjectMapper json,
        @Value("${barofit.identity.base-url:http://localhost:3004}") String identityBaseUrl) {
        this.json = json;
        this.profileUri = URI.create(identityBaseUrl + "/api/v1/users/me");
    }

    /** 프로필 장애나 온보딩 전에도 운동을 계속할 수 있도록 차분한 스타일을 사용한다. */
    public CoachingStyle style(UUID userId, String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder(profileUri).timeout(Duration.ofSeconds(3))
                .header("Authorization", "Bearer " + accessToken).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return CoachingStyle.CALM;
            JsonNode me = json.readTree(response.body());
            if (!userId.toString().equals(me.path("id").asText()) || !me.path("isOnboarded").asBoolean())
                return CoachingStyle.CALM;
            return CoachingStyle.valueOf(me.path("profile").path("coachingStyle").asText());
        } catch (Exception ignored) {
            return CoachingStyle.CALM;
        }
    }
}
