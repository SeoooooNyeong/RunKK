package com.a207.runkk.realtime;

import com.a207.runkk.battle.BattleStartService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.crew.chat.CrewChatService;
import com.a207.runkk.member.AccessGrant;
import com.a207.runkk.member.TokenIssuer;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
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
 * WebSocket 메시지를 받아 채널별 서비스로 넘긴다(설계 6.1.13, 5.7.2). 연결 때 TokenHandshakeInterceptor가 붙인 회원 ID와 접근 토큰
 * 만료 시각으로 SocketSessionRegistry.register를 부르고(만료 시각에 4401 예약, D-110), 속성이 없으면 핸드셰이크 Authorization
 * 헤더를 member.TokenIssuer.verifyGrant로 다시 확인하며(design-review.md E1) 실패하면 정책 위반 코드로 닫는다. 등록하면 우편함을
 * 비우고 출발 예약된 대결의 BattleStart를 다시 보낸다(배틀런 QA 1·2번). 메시지를 받을 때마다 먼저 만료를 확인해 지났으면 4401 TOKEN_EXPIRED로
 * 닫는다(예약 작업이 늦을 때의 방어). 본문은 InboundMessage로 읽어 진행값은 ProgressDispatcher로, 채팅방
 * 입장(InboundChatPresence)은 crew.chat의 checkRoomAccess를 통과할 때만 ChatPresenceRegistry에 반영한다(크루 밖이면
 * 기록하지 않고 연결은 유지, N11). 소켓이 닫히면 그 세션이 남긴 방 입장 상태를 모두 지운다. 형식이 틀린 메시지는 버린다. 토큰·진행값은 로그에 남기지 않는다.
 */
@Component
@ConditionalOnProperty(name = "runkk.live", havingValue = "websocket", matchIfMissing = true)
public class LiveSocketHandler extends TextWebSocketHandler {

    static final String MEMBER_ID = "memberId";

    /** 세션 속성: 핸드셰이크 접근 토큰의 만료 시각(Instant, D-110). */
    static final String EXPIRES_AT = "expiresAt";

    private static final Logger log = LoggerFactory.getLogger(LiveSocketHandler.class);

    /** 진행값 메시지 한 개의 크기 상한(항목 5개, 약 150바이트). 넘으면 읽지 않고 버린다. */
    static final int MAX_MESSAGE_BYTES = 1_024;

    private final TokenIssuer tokenIssuer;
    private final SocketSessionRegistry registry;
    private final WebSocketMessenger messenger;
    private final RealtimeMessageSerializer serializer;
    private final ProgressDispatcher dispatcher;
    private final BattleStartService battleStartService;
    private final CrewChatService chatService;
    private final ChatPresenceRegistry chatPresence;
    private final Clock clock;

    LiveSocketHandler(
            TokenIssuer tokenIssuer,
            SocketSessionRegistry registry,
            WebSocketMessenger messenger,
            RealtimeMessageSerializer serializer,
            ProgressDispatcher dispatcher,
            BattleStartService battleStartService,
            CrewChatService chatService,
            ChatPresenceRegistry chatPresence,
            Clock clock) {
        this.tokenIssuer = tokenIssuer;
        this.registry = registry;
        this.messenger = messenger;
        this.serializer = serializer;
        this.dispatcher = dispatcher;
        this.battleStartService = battleStartService;
        this.chatService = chatService;
        this.chatPresence = chatPresence;
        this.clock = clock;
    }

    /**
     * 핸드셰이크가 붙인 회원 ID·만료 시각을 쓴다(TokenHandshakeInterceptor). 없으면(인터셉터 없이 연결) 헤더로 다시 확인한다. 등록이 만료 시각에
     * 4401로 닫는 작업을 예약한다(D-110). 우편함을 비운 뒤 BattleStart를 다시 보낸다(resendStart).
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        Optional<AccessGrant> grant = grantOf(session);
        if (grant.isEmpty()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("인증 실패"));
            return;
        }
        long memberId = grant.get().memberId();
        session.getAttributes().put(MEMBER_ID, memberId);
        session.getAttributes().put(EXPIRES_AT, grant.get().expiresAt());
        registry.register(memberId, session, grant.get().expiresAt());
        messenger.flush(memberId);
        resendStart(memberId);
    }

    /** 출발 예약 때 보낸 BattleStart가 재연결 사이에 사라졌을 수 있어 다시 보낸다. 실패해도 연결은 유지한다. */
    private void resendStart(long memberId) {
        try {
            battleStartService.resendStart(memberId);
        } catch (RuntimeException e) {
            log.warn("socket_battle_start_resend_failed error={}", e.getClass().getSimpleName(), e);
        }
    }

    private Optional<AccessGrant> grantOf(WebSocketSession session) {
        if (session.getAttributes().get(MEMBER_ID) instanceof Long id
                && session.getAttributes().get(EXPIRES_AT) instanceof Instant expiresAt) {
            return Optional.of(new AccessGrant(id, expiresAt));
        }
        return TokenHandshakeInterceptor.grantOf(
                tokenIssuer, session.getHandshakeHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Object memberId = session.getAttributes().get(MEMBER_ID);
        if (!(memberId instanceof Long id)) {
            return;
        }
        if (isExpired(session)) {
            log.info("socket_token_expired memberId={} on=message", id);
            SocketSessionRegistry.close(session, SocketSessionRegistry.TOKEN_EXPIRED);
            return;
        }
        if (message.getPayloadLength() > MAX_MESSAGE_BYTES) {
            return;
        }
        Optional<InboundMessage> inbound = serializer.readInbound(message.getPayload());
        if (inbound.isEmpty()) {
            log.debug("socket_message_dropped reason=format");
            return;
        }
        switch (inbound.get()) {
            case InboundProgress progress -> dispatch(id, progress);
            case InboundChatPresence presence -> applyChatPresence(session, id, presence);
        }
    }

    private void dispatch(long memberId, InboundProgress progress) {
        try {
            dispatcher.dispatch(memberId, progress);
        } catch (RuntimeException e) {
            log.warn("socket_progress_failed error={}", e.getClass().getSimpleName(), e);
        }
    }

    /**
     * 방 입장은 크루 구성원일 때만 기록한다(12.2 N11). 같은 세션이 이미 확인한 방은 다시 확인하지 않고 세션당 서로 다른 방 10개까지만 확인한다(D-173).
     * 퇴장은 확인 없이 뺀다.
     */
    private void applyChatPresence(
            WebSocketSession session, long memberId, InboundChatPresence presence) {
        if (!presence.present()) {
            chatPresence.leave(presence.crewId(), memberId);
            return;
        }
        String sessionId = session.getId();
        long crewId = presence.crewId();
        if (chatPresence.isChecked(sessionId, crewId)) { // 이미 확인한 방은 다시 확인하지 않는다(D-173)
            if (chatPresence.wasAllowed(sessionId, crewId)) {
                chatPresence.enter(crewId, memberId, sessionId);
            }
            return;
        }
        if (!chatPresence.canCheckAnother(sessionId)) {
            log.info("chat_presence_dropped memberId={} reason=too_many_rooms", memberId);
            return;
        }
        try {
            chatService.checkRoomAccess(memberId, crewId);
            chatPresence.markChecked(sessionId, crewId, true);
            chatPresence.enter(crewId, memberId, sessionId);
        } catch (BusinessRuleException e) {
            chatPresence.markChecked(sessionId, crewId, false);
            log.info("chat_presence_rejected memberId={} reason={}", memberId, e.reason());
        } catch (RuntimeException e) {
            log.warn("chat_presence_failed error={}", e.getClass().getSimpleName(), e);
        }
    }

    /** 접근 토큰 만료 시각이 지났는가(정각 포함). 시각을 모르면 지난 것으로 본다. */
    private boolean isExpired(WebSocketSession session) {
        return !(session.getAttributes().get(EXPIRES_AT) instanceof Instant expiresAt)
                || !clock.instant().isBefore(expiresAt);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object memberId = session.getAttributes().get(MEMBER_ID);
        if (memberId instanceof Long id) {
            registry.unregister(id, session);
            chatPresence.leaveAll(id, session.getId()); // 새 연결이 남긴 입장은 그대로 둔다
        }
    }
}
