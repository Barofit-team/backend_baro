package com.barofit.workout;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class CoachingWebSocketConfig implements WebSocketConfigurer {
    private final CoachingWebSocketHandler handler;

    public CoachingWebSocketConfig(CoachingWebSocketHandler handler) { this.handler = handler; }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 네이티브 앱은 Origin 헤더를 보내지 않는다. 브라우저 클라이언트도 첫 메시지 JWT 검증이 필수다.
        registry.addHandler(handler, "/ws/coaching").setAllowedOriginPatterns("*");
    }
}
