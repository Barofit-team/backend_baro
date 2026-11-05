package com.barofit.workout;

import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ElevenLabsClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json;
    private final String key;
    private final String voiceId;
    private final String modelId;

    public ElevenLabsClient(ObjectMapper json,
        @Value("${barofit.elevenlabs.api-key:}") String key,
        @Value("${barofit.elevenlabs.voice-id:}") String voiceId,
        @Value("${barofit.elevenlabs.model-id:eleven_multilingual_v2}") String modelId) {
        this.json = json;
        this.key = key;
        this.voiceId = voiceId;
        this.modelId = modelId;
    }

    public boolean configured() { return !key.isBlank() && !voiceId.isBlank(); }

    public byte[] synthesize(String text) throws IOException, InterruptedException {
        if (!configured()) throw new IOException("ElevenLabs credentials are not configured");
        if (text.length() > 300) throw new IOException("Coaching text is too long");
        String body = json.writeValueAsString(Map.of("text", text, "model_id", modelId));
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                "https://api.elevenlabs.io/v1/text-to-speech/" + voiceId + "?output_format=mp3_44100_128"))
            .timeout(Duration.ofSeconds(10))
            .header("xi-api-key", key)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var stream = response.body()) {
            if (response.statusCode() != 200) throw new IOException("ElevenLabs returned HTTP " + response.statusCode());
            byte[] audio = stream.readNBytes(1_000_001);
            if (audio.length == 0 || audio.length > 1_000_000) throw new IOException("Invalid ElevenLabs audio size");
            return audio;
        }
    }
}
