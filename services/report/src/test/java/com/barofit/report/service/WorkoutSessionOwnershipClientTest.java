package com.barofit.report.service;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class WorkoutSessionOwnershipClientTest {
    @Test
    void forwardsTokenAndRequiresAnOwnedSession() throws Exception {
        UUID sessionId = UUID.randomUUID();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> bearer = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/workout-sessions", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            bearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            var client = new WorkoutSessionOwnershipClient(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
            client.requireOwned(sessionId, "Bearer user-token");
            assertEquals("/api/v1/workout-sessions/" + sessionId, path.get());
            assertEquals("Bearer user-token", bearer.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsMissingOrUnownedSession() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/workout-sessions", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            var client = new WorkoutSessionOwnershipClient(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
            var missing = assertThrows(ResponseStatusException.class,
                () -> client.requireOwned(UUID.randomUUID(), "Bearer user-token"));
            assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
            var unauthenticated = assertThrows(ResponseStatusException.class,
                () -> client.requireOwned(UUID.randomUUID(), null));
            assertEquals(HttpStatus.UNAUTHORIZED, unauthenticated.getStatusCode());
        } finally {
            server.stop(0);
        }
    }
}
