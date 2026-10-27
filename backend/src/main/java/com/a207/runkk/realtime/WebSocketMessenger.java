package com.a207.runkk.realtime;

import com.a207.runkk.battle.BattleMessage;
import com.a207.runkk.battle.BattleMessenger;
import com.a207.runkk.crew.CrewRunMessage;
import com.a207.runkk.crew.CrewRunMessenger;
import com.a207.runkk.crew.chat.CrewChatMessenger;
import com.a207.runkk.crew.chat.CrewChatSignal;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * battle.BattleMessenger·crew.CrewRunMessenger·crew.chat.CrewChatMessenger의 WebSocket 구현(설계 6.1.13,
 * D-06, D-101). 대결·크루런 메시지는 연결이 없거나 보내기에 실패하면 버리지 않고 30초 우편함에 두었다가 재연결 때 LiveSocketHandler가 flush로
 * 보낸다. 채팅 메시지는 우편함에 넣지 않고 연결이 없으면 버린다(앱이 재연결 뒤 ?after=로 메운다, NFR-040).
 */
@Component
@ConditionalOnProperty(name = "runkk.live", havingValue = "websocket", matchIfMissing = true)
public class WebSocketMessenger implements BattleMessenger, CrewRunMessenger, CrewChatMessenger {

    private static final Logger log = LoggerFactory.getLogger(WebSocketMessenger.class);

    private final SocketSessionRegistry registry;
    private final RealtimeMessageSerializer serializer;
    private final MessageMailbox mailbox;

    WebSocketMessenger(
            SocketSessionRegistry registry,
            RealtimeMessageSerializer serializer,
            MessageMailbox mailbox) {
        this.registry = registry;
        this.serializer = serializer;
        this.mailbox = mailbox;
    }

    @Override
    public void send(long memberId, BattleMessage m) {
        deliver(memberId, serializer.serialize(m));
    }

    @Override
    public void sendAll(Collection<Long> memberIds, BattleMessage m) {
        String json = serializer.serialize(m);
        memberIds.forEach(id -> deliver(id, json));
    }

    @Override
    public void send(long memberId, CrewRunMessage m) {
        deliver(memberId, serializer.serialize(m.forRecipient(memberId)));
    }

    /** 받는 사람마다 직렬화한다(중계의 mine, D-161). */
    @Override
    public void sendAll(Collection<Long> memberIds, CrewRunMessage m) {
        memberIds.forEach(id -> send(id, m));
    }

    @Override
    public void send(Collection<Long> memberIds, CrewChatSignal signal) {
        TextMessage json = new TextMessage(serializer.serialize(signal));
        for (long memberId : memberIds) {
            registry.find(memberId).ifPresent(session -> sendOrDrop(session, json));
        }
    }

    /** 채팅 전송: 실패하면 버린다(우편함 없음, D-101). */
    private static void sendOrDrop(WebSocketSession session, TextMessage json) {
        try {
            session.sendMessage(json);
        } catch (IOException | RuntimeException e) {
            log.debug("socket_chat_send_dropped error={}", e.getClass().getSimpleName());
        }
    }

    /** 재연결한 회원에게 우편함의 메시지를 보낸다. */
    void flush(long memberId) {
        mailbox.drain(memberId).forEach(json -> deliver(memberId, json));
    }

    private void deliver(long memberId, String json) {
        Optional<WebSocketSession> session = registry.find(memberId);
        if (session.isEmpty()) {
            mailbox.put(memberId, json);
            return;
        }
        try {
            session.get().sendMessage(new TextMessage(json));
        } catch (IOException | RuntimeException e) {
            // 느린 연결의 버퍼·전송 시간 초과(SessionLimitExceededException)도 우편함으로 돌려 호출한 쪽을 막지 않는다
            log.debug("socket_send_failed error={}", e.getClass().getSimpleName());
            mailbox.put(memberId, json);
        }
    }
}
