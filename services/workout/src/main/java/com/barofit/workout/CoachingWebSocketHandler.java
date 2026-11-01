package com.barofit.workout;

import com.barofit.workout.WorkoutDtos.SessionResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class CoachingWebSocketHandler extends TextWebSocketHandler {
    private final JwtDecoder decoder;
    private final WorkoutSessionService sessions;
    private final ElevenLabsClient elevenLabs;
    private final CoachingProfileClient profiles;
    private final OpenAiFeedbackClient feedback;
    private final ObjectMapper json;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    public CoachingWebSocketHandler(JwtDecoder decoder, WorkoutSessionService sessions,
                                    ElevenLabsClient elevenLabs, CoachingProfileClient profiles,
                                    OpenAiFeedbackClient feedback, ObjectMapper json) {
        this.decoder = decoder;
        this.sessions = sessions;
        this.elevenLabs = elevenLabs;
        this.profiles = profiles;
        this.feedback = feedback;
        this.json = json;
    }

    @Override
    protected void handleTextMessage(WebSocketSession socket, TextMessage raw) throws Exception {
        if (raw.getPayloadLength() > 2048) { socket.close(CloseStatus.TOO_BIG_TO_PROCESS); return; }
        JsonNode message;
        try { message = json.readTree(raw.getPayload()); }
        catch (Exception ex) { socket.close(CloseStatus.BAD_DATA); return; }
        if (socket.getAttributes().get("userId") == null) {
            authenticate(socket, message);
            return;
        }
        if (!"COACH_EVENT".equals(message.path("type").asText())) {
            socket.close(CloseStatus.BAD_DATA); return;
        }
        UUID userId = (UUID) socket.getAttributes().get("userId");
        UUID sessionId = (UUID) socket.getAttributes().get("sessionId");
        String event = message.path("eventType").asText();
        SessionResponse session;
        try { session = sessions.get(userId, sessionId); }
        catch (Exception ex) { socket.close(CloseStatus.POLICY_VIOLATION); return; }
        if ((session.status() == SessionStatus.COMPLETED || session.status() == SessionStatus.ABANDONED)
            && !event.equals("WORKOUT_FINISH")) {
            socket.close(CloseStatus.POLICY_VIOLATION); return;
        }
        CoachingStyle style = (CoachingStyle) socket.getAttributes().get("coachingStyle");
        String text = CoachingMessageCatalog.text(style, event, message, session.routineSnapshot());
        if (text == null) return;
        long now = System.currentTimeMillis();
        String rateKey = "lastAudioAt:" + event + ":" + text;
        Long last = (Long) socket.getAttributes().get(rateKey);
        if (last != null && now - last < 4000) return;
        socket.getAttributes().put(rateKey, now);
        long sequence = ((AtomicLong) socket.getAttributes().get("coachingSequence")).incrementAndGet();
        workers.submit(() -> sendCoaching(socket, event, text, message, style, sequence));
    }

    private void authenticate(WebSocketSession socket, JsonNode message) throws IOException {
        if (!"AUTH".equals(message.path("type").asText())) {
            socket.close(CloseStatus.POLICY_VIOLATION); return;
        }
        try {
            String token = message.path("accessToken").asText();
            UUID sessionId = UUID.fromString(message.path("sessionId").asText());
            UUID userId = UUID.fromString(decoder.decode(token).getSubject());
            sessions.get(userId, sessionId);
            CoachingStyle style = profiles.style(userId, token);
            socket.getAttributes().put("userId", userId);
            socket.getAttributes().put("sessionId", sessionId);
            socket.getAttributes().put("coachingStyle", style);
            socket.getAttributes().put("coachingSequence", new AtomicLong());
            socket.getAttributes().put("gptInFlight", new AtomicBoolean());
            socket.sendMessage(new TextMessage(json.writeValueAsString(Map.of(
                "type", "AUTH_OK", "text", "연결되었습니다.", "coachingStyle", style.name()))));
        } catch (Exception ex) {
            socket.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    private void sendCoaching(WebSocketSession socket, String event, String fallback, JsonNode message,
                              CoachingStyle style, long sequence) {
        String text = fallback;
        AtomicBoolean inFlight = (AtomicBoolean) socket.getAttributes().get("gptInFlight");
        boolean generating = event.equals("SET_FEEDBACK") && feedback.configured()
            && CoachingMessageCatalog.finding(message.path("feedbackCode").asText()) != null
            && inFlight.compareAndSet(false, true);
        try {
            if (generating) {
                try {
                    String code = message.path("feedbackCode").asText();
                    text = feedback.generate(style, code, CoachingMessageCatalog.finding(code),
                        boundedInt(message.path("actualReps"), 1000),
                        boundedInt(message.path("actualDurationSeconds"), 3600));
                } catch (Exception ignored) {
                    text = fallback;
                }
            }
            if (!current(socket, sequence)) return;
            byte[] audio = elevenLabs.synthesize(text);
            Map<String, String> payload = Map.of("type", event, "text", text,
                "audioBase64", Base64.getEncoder().encodeToString(audio), "timestamp", Instant.now().toString());
            synchronized (socket) {
                if (current(socket, sequence)) socket.sendMessage(new TextMessage(json.writeValueAsString(payload)));
            }
        } catch (Exception ex) {
            // 음성 생성 실패는 운동 진행을 막지 않는다. 앱은 로컬 TTS로 대체할 수 있다.
            try {
                synchronized (socket) {
                    if (current(socket, sequence)) socket.sendMessage(new TextMessage(json.writeValueAsString(Map.of(
                        "type", event, "text", text, "timestamp", Instant.now().toString()))));
                }
            } catch (Exception ignored) { }
        } finally {
            if (generating) inFlight.set(false);
        }
    }

    private boolean current(WebSocketSession socket, long sequence) {
        return socket.isOpen() && ((AtomicLong) socket.getAttributes().get("coachingSequence")).get() == sequence;
    }

    private static Integer boundedInt(JsonNode value, int max) {
        return value.isInt() && value.asInt() >= 0 && value.asInt() <= max ? value.asInt() : null;
    }

    @PreDestroy public void shutdown() { workers.close(); }
}
