package com.a207.runkk.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 경로 /ws 하나를 연다(설계 6.1.13, 5.7.2). runkk.live=polling이면 열지 않아 앱이 핸드셰이크 404로 폴링 채널로
 * 바꾼다(D-57). STOMP를 쓰지 않고 메시지 type 필드로 나눈다.
 */
@Configuration
@EnableWebSocket
@ConditionalOnProperty(name = "runkk.live", havingValue = "websocket", matchIfMissing = true)
public class WebSocketConfig implements WebSocketConfigurer {

    /** 실시간 경로(5.7.2). REST(/api/v1) 밖이다. */
    public static final String PATH = "/ws";

    private final LiveSocketHandler handler;

    public WebSocketConfig(LiveSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, PATH);
    }
}
