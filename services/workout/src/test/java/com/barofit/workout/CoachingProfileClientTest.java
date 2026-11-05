package com.barofit.workout;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CoachingProfileClientTest {
    @Test
    void readsStyleOnlyForAuthenticatedUserAndFallsBackWhenUnavailable() throws Exception {
        UUID user = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/users/me", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-token");
            byte[] body = ("{\"id\":\"" + user + "\",\"isOnboarded\":true,"
                + "\"profile\":{\"coachingStyle\":\"SPARTA\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            CoachingProfileClient client = new CoachingProfileClient(new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort());
            assertThat(client.style(user, "test-token")).isEqualTo(CoachingStyle.SPARTA);
            assertThat(client.style(UUID.randomUUID(), "test-token")).isEqualTo(CoachingStyle.CALM);
        } finally {
            server.stop(0);
        }
    }
}
