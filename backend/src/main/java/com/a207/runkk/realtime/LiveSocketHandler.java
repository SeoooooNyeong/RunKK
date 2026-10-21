package com.a207.runkk.realtime;

import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.TokenIssuer;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket 메시지를 받아 채널별 서비스로 넘긴다(설계 6.1.13, 5.7.2). 연결 때 핸드셰이크 Authorization 헤더의 접근 토큰을
 * member.TokenIssuer.verify로 확인해 회원 ID를 정하고(design-review.md E1), 실패하면 정책 위반 코드로 닫는다. 앱 → 서버는 진행값
 * 메시지 하나뿐이고 형식이 틀린 메시지는 버린다. 토큰·진행값은 로그에 남기지 않는다.
 */
@Component
@ConditionalOnProperty(name = "runkk.live", havingValue = "websocket", matchIfMissing = true)
public class LiveSocketHandler extends TextWebSocketHandler {

    static final String MEMBER_ID = "memberId";

    private static final Logger log = LoggerFactory.getLogger(LiveSocketHandler.class);
    private static final String BEARER = "Bearer ";

    /** 진행값 메시지 한 개의 크기 상한(항목 5개, 약 150바이트). 넘으면 읽지 않고 버린다. */
    static final int MAX_MESSAGE_BYTES = 1_024;

    private final TokenIssuer tokenIssuer;
    private final SocketSessionRegistry registry;
    private final WebSocketMessenger messenger;
    private final RealtimeMessageSerializer serializer;
    private final ProgressDispatcher dispatcher;

    LiveSocketHandler(
            TokenIssuer tokenIssuer,
            SocketSessionRegistry registry,
            WebSocketMessenger messenger,
            RealtimeMessageSerializer serializer,
            ProgressDispatcher dispatcher) {
        this.tokenIssuer = tokenIssuer;
        this.registry = registry;
        this.messenger = messenger;
        this.serializer = serializer;
        this.dispatcher = dispatcher;
    }

    /** 핸드셰이크가 붙인 회원 ID를 쓴다(TokenHandshakeInterceptor). 없으면(인터셉터 없이 연결) 헤더로 다시 확인한다. */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        Optional<Long> memberId =
                session.getAttributes().get(MEMBER_ID) instanceof Long id
                        ? Optional.of(id)
                        : authenticate(session);
        if (memberId.isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("인증 실패"));
            return;
        }
        session.getAttributes().put(MEMBER_ID, memberId.get());
        registry.register(memberId.get(), session);
        messenger.flush(memberId.get());
    }

    private Optional<Long> authenticate(WebSocketSession session) {
        String header = session.getHandshakeHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            return Optional.empty();
        }
        try {
            return Optional.of(tokenIssuer.verify(header.substring(BEARER.length())));
        } catch (AuthFailureException e) {
            log.info("socket_auth_failed");
            return Optional.empty();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Object memberId = session.getAttributes().get(MEMBER_ID);
        if (!(memberId instanceof Long id) || message.getPayloadLength() > MAX_MESSAGE_BYTES) {
            return;
        }
        Optional<InboundProgress> inbound = serializer.readInbound(message.getPayload());
        if (inbound.isEmpty()) {
            log.debug("socket_message_dropped reason=format");
            return;
        }
        try {
            dispatcher.dispatch(id, inbound.get());
        } catch (RuntimeException e) {
            log.warn("socket_progress_failed error={}", e.getClass().getSimpleName(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object memberId = session.getAttributes().get(MEMBER_ID);
        if (memberId instanceof Long id) {
            registry.unregister(id, session);
        }
    }
}
