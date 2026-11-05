package com.barofit.workout;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 세트 종료 판정의 짧은 코칭 문장만 생성한다. 생성 결과는 저장하지 않는다. */
@Component
public class OpenAiFeedbackClient {
    private static final String INSTRUCTIONS = """
        한국어 운동 코치입니다. 제공된 자세 판정 사실만 사용해 세트 종료 후 말할 한 문장을 만드세요.
        판정 코드를 바꾸거나 없던 오류·성과·횟수를 만들지 마세요.
        의료 진단, 통증을 참고 계속하라는 지시, 모욕이나 비난은 하지 마세요.
        80자 이내로 자연스럽게 말하고 JSON의 text 필드만 출력하세요.
        """;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json;
    private final String key;
    private final String model;
    private final URI endpoint;

    public OpenAiFeedbackClient(ObjectMapper json,
        @Value("${barofit.openai.api-key:}") String key,
        @Value("${barofit.openai.model:gpt-6-luna}") String model,
        @Value("${barofit.openai.base-url:https://api.openai.com}") String baseUrl) {
        this.json = json;
        this.key = key;
        this.model = model;
        this.endpoint = URI.create(baseUrl + "/v1/responses");
    }

    public boolean configured() { return !key.isBlank(); }

    public String generate(CoachingStyle style, String feedbackCode, String finding,
                           Integer actualReps, Integer actualDurationSeconds) throws IOException, InterruptedException {
        if (!configured()) throw new IOException("OpenAI credentials are not configured");
        String styleGuide = switch (style) {
            case FRIENDLY -> "친근하고 따뜻하게 격려하되 교정 지시는 분명하게";
            case CALM -> "차분하고 간결하게";
            case SPARTA -> "힘있고 단호하게, 다만 압박·모욕 없이";
            case ANALYTICAL -> "관찰된 사실과 다음 동작을 구체적으로";
        };
        Map<String, Object> facts = new java.util.LinkedHashMap<>();
        facts.put("coachingStyle", style.name());
        facts.put("styleInstruction", styleGuide);
        facts.put("feedbackCode", feedbackCode);
        facts.put("observedFinding", finding);
        if (actualReps != null) facts.put("actualReps", actualReps);
        if (actualDurationSeconds != null) facts.put("actualDurationSeconds", actualDurationSeconds);
        Map<String, Object> format = Map.of(
            "type", "json_schema", "name", "workout_feedback", "strict", true,
            "schema", Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of("text", Map.of("type", "string")), "required", List.of("text")));
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", model);
        body.put("store", false);
        body.put("max_output_tokens", 180);
        if (model.equals("gpt-6-luna")) body.put("reasoning", Map.of("effort", "none"));
        body.put("instructions", INSTRUCTIONS);
        body.put("input", json.writeValueAsString(facts));
        body.put("text", Map.of("format", format));
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
            .header("Authorization", "Bearer " + key)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("OpenAI returned HTTP " + response.statusCode());
        JsonNode result = json.readTree(response.body());
        if (!"completed".equals(result.path("status").asText()))
            throw new IOException("OpenAI response was not completed");
        for (JsonNode item : result.path("output")) {
            if (!"message".equals(item.path("type").asText())) continue;
            for (JsonNode content : item.path("content")) {
                if (!"output_text".equals(content.path("type").asText())) continue;
                String text = json.readTree(content.path("text").asText()).path("text").asText("").trim();
                if (text.isBlank() || text.length() > 120 || text.contains("\n") || text.contains("\r"))
                    throw new IOException("OpenAI feedback did not meet the length and format rules");
                return text;
            }
        }
        throw new IOException("OpenAI response did not contain feedback text");
    }
}
