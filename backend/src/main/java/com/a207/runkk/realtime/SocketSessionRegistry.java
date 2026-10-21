package com.a207.runkk.realtime;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * 회원 ID ↔ WebSocket 세션 대응(설계 6.1.13). 회원마다 세션 하나이고 새로 연결하면 옛 세션을 닫는다. 보내기는 여러 스레드에서 오므로 세션을 동시 송신
 * 장식으로 감싼다. 서버가 내려갈 때 "서버 재시작" 닫기 코드를 보내 앱이 재연결하게 한다(5.5 종료).
 */
@Component
@ConditionalOnProperty(name = "runkk.live", havingValue = "websocket", matchIfMissing = true)
public class SocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SocketSessionRegistry.class);
    private static final int SEND_TIME_LIMIT_MILLIS = 2_000;
    private static final int BUFFER_LIMIT_BYTES = 64 * 1024;

    private final Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(long memberId, WebSocketSession session) {
        WebSocketSession wrapped =
                new ConcurrentWebSocketSessionDecorator(
                        session, SEND_TIME_LIMIT_MILLIS, BUFFER_LIMIT_BYTES);
        WebSocketSession previous = sessions.put(memberId, wrapped);
        if (previous != null && !previous.getId().equals(session.getId())) {
            close(previous, CloseStatus.NORMAL);
        }
    }

    /** 그 세션이 지금 등록된 세션일 때만 뺀다(새 연결이 먼저 등록됐을 수 있다). */
    public void unregister(long memberId, WebSocketSession session) {
        sessions.computeIfPresent(
                memberId,
                (id, current) -> current.getId().equals(session.getId()) ? null : current);
    }

    public Optional<WebSocketSession> find(long memberId) {
        return Optional.ofNullable(sessions.get(memberId)).filter(WebSocketSession::isOpen);
    }

    @PreDestroy
    void closeAllForRestart() {
        sessions.values().forEach(s -> close(s, CloseStatus.SERVICE_RESTARTED));
        sessions.clear();
    }

    private static void close(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.debug("socket_close_failed error={}", e.getClass().getSimpleName());
        }
    }
}
