package com.barofit.workout;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiFeedbackClientTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void sendsOnlyValidatedFactsWithoutStorageAndReadsStructuredFeedback() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = server((exchange) -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-key");
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String feedback = json.writeValueAsString(java.util.Map.of("text", "무릎 방향을 발끝과 맞춰주세요."));
            respond(exchange, 200, json.writeValueAsString(java.util.Map.of("status", "completed",
                "output", java.util.List.of(java.util.Map.of("type", "message",
                    "content", java.util.List.of(java.util.Map.of("type", "output_text", "text", feedback)))))));
        });
        try {
            OpenAiFeedbackClient client = client(server, "test-key");
            assertThat(client.generate(CoachingStyle.CALM, "KNEE_IN",
                CoachingMessageCatalog.finding("KNEE_IN"), 12, null))
                .isEqualTo("무릎 방향을 발끝과 맞춰주세요.");
            var request = json.readTree(requestBody.get());
            assertThat(request.path("store").asBoolean()).isFalse();
            assertThat(request.path("model").asText()).isEqualTo("gpt-6-luna");
            assertThat(request.path("text").path("format").path("type").asText()).isEqualTo("json_schema");
            var facts = json.readTree(request.path("input").asText());
            assertThat(facts.path("feedbackCode").asText()).isEqualTo("KNEE_IN");
            assertThat(facts.path("actualReps").asInt()).isEqualTo(12);
            assertThat(requestBody.get()).doesNotContain("sessionId", "userId", "feedbackText");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsEmptyOrOverlongModelText() throws Exception {
        HttpServer server = server(exchange -> {
            String feedback = json.writeValueAsString(java.util.Map.of("text", ""));
            respond(exchange, 200, json.writeValueAsString(java.util.Map.of("status", "completed",
                "output", java.util.List.of(java.util.Map.of("type", "message",
                    "content", java.util.List.of(java.util.Map.of("type", "output_text", "text", feedback)))))));
        });
        try {
            assertThatThrownBy(() -> client(server, "test-key").generate(CoachingStyle.FRIENDLY,
                "GOOD_FORM", CoachingMessageCatalog.finding("GOOD_FORM"), null, 30))
                .isInstanceOf(java.io.IOException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingKeyDoesNotCallProvider() {
        OpenAiFeedbackClient client = new OpenAiFeedbackClient(json, "", "gpt-6-luna", "http://127.0.0.1:1");
        assertThat(client.configured()).isFalse();
        assertThatThrownBy(() -> client.generate(CoachingStyle.CALM, "KNEE_IN", "무릎 안쪽 이동", null, null))
            .isInstanceOf(java.io.IOException.class);
    }

    private OpenAiFeedbackClient client(HttpServer server, String key) {
        return new OpenAiFeedbackClient(json, key, "gpt-6-luna",
            "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", handler);
        server.start();
        return server;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
