package com.barofit.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 각 서비스가 소유한 데이터를 사용자 토큰으로 조회한다. */
@Component
public class ReportSourceClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper;
    private final URI workout;
    private final URI routine;

    public ReportSourceClient(ObjectMapper mapper,
        @Value("${barofit.workout.base-url:http://localhost:3006}") String workout,
        @Value("${barofit.routine.base-url:http://localhost:3005}") String routine) {
        this.mapper = mapper;
        this.workout = URI.create(workout);
        this.routine = URI.create(routine);
    }

    public JsonNode workout(String path, String auth) { return get(workout.resolve(path), auth); }
    public JsonNode routine(String path, String auth) { return get(routine.resolve(path), auth); }

    private JsonNode get(URI uri, String auth) {
        if (auth == null || !auth.startsWith("Bearer ") || auth.substring(7).isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        try {
            var req = HttpRequest.newBuilder(uri).header("Authorization", auth)
                .timeout(Duration.ofSeconds(8)).GET().build();
            var response = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404)
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout or plan not found");
            if (response.statusCode() == 401 || response.statusCode() == 403)
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication failed");
            if (response.statusCode() != 200)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Source service unavailable");
            return mapper.readTree(response.body());
        } catch (ResponseStatusException ex) { throw ex; }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Source service interrupted", ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Source service unavailable", ex);
        }
    }
}
