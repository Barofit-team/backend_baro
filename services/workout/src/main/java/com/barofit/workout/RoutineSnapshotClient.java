package com.barofit.workout;

import com.barofit.common.error.BusinessException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 첫 세션 저장 시 Routine 서비스가 인정하는 템플릿 또는 내 루틴을 조회한다. */
@Component
public class RoutineSnapshotClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json;
    private final URI baseUri;

    public RoutineSnapshotClient(ObjectMapper json,
            @Value("${barofit.routine.base-url:http://localhost:3005}") String baseUrl) {
        this.json = json;
        this.baseUri = URI.create(baseUrl);
    }

    public JsonNode fetch(UUID routineId, String type, String accessToken) {
        if (accessToken == null || accessToken.isBlank())
            throw new BusinessException(WorkoutErrorCode.AUTHENTICATION_REQUIRED);
        String path = "TEMPLATE".equals(type) ? "/api/v1/routine-templates/" : "/api/v1/routines/";
        try {
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path + routineId))
                .header("Authorization", "Bearer " + accessToken)
                .timeout(Duration.ofSeconds(4)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 || response.statusCode() == 403)
                throw new BusinessException(WorkoutErrorCode.ROUTINE_NOT_FOUND);
            if (response.statusCode() == 401)
                throw new BusinessException(WorkoutErrorCode.AUTHENTICATION_REQUIRED);
            if (response.statusCode() != 200)
                throw new BusinessException(WorkoutErrorCode.ROUTINE_UNAVAILABLE);
            JsonNode detail = json.readTree(response.body());
            if (!routineId.toString().equals(detail.path("id").asText())
                || !type.equals(detail.path("type").asText()))
                throw new BusinessException(WorkoutErrorCode.ROUTINE_UNAVAILABLE);
            return detail;
        } catch (BusinessException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(WorkoutErrorCode.ROUTINE_UNAVAILABLE);
        } catch (Exception ex) {
            throw new BusinessException(WorkoutErrorCode.ROUTINE_UNAVAILABLE);
        }
    }
}
