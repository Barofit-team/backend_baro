package com.barofit.report.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Workout 서비스가 소유한 세션을 호출자의 JWT로 확인한다. */
@Component
public class WorkoutSessionOwnershipClient {
    private final HttpClient http;
    private final URI baseUri;

    @Autowired
    public WorkoutSessionOwnershipClient(@Value("${barofit.workout.base-url:http://localhost:3006}") String baseUrl) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), URI.create(baseUrl));
    }

    WorkoutSessionOwnershipClient(HttpClient http, URI baseUri) {
        this.http = http;
        this.baseUri = baseUri;
    }

    public void requireOwned(UUID sessionId, String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")
            || authorization.substring(7).isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        URI uri = baseUri.resolve("/api/v1/workout-sessions/" + sessionId);
        HttpRequest request = HttpRequest.newBuilder(uri)
            .header("Authorization", authorization)
            .timeout(Duration.ofSeconds(3))
            .GET().build();
        try {
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 200) return;
            if (status == 401 || status == 403) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Workout authentication failed");
            }
            if (status == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout session not found");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Workout ownership lookup failed");
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Workout ownership lookup interrupted", ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Workout ownership lookup failed", ex);
        }
    }
}
