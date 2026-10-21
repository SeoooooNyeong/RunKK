package com.a207.runkk.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.battle.LiveBattleService;
import com.a207.runkk.battle.live.BattleEnded;
import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.TokenIssuer;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** realtime 단위: 우편함 30초, 연결 없을 때 보관·재연결 때 전달, 핸드셰이크 인증, 진행값 전달, 폴링 우편함. */
@DisplayName("realtime 메신저·핸들러")
class RealtimeUnitTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 19, 0);
    private static final String PROGRESS =
            "{\"channel\":\"BATTLE\",\"channelId\":7,\"distanceMeters\":100,"
                    + "\"elapsedSeconds\":30,\"sentAt\":\"2026-10-21T19:00:30\"}";

    /** 시각을 옮길 수 있는 시계. */
    static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(LocalDateTime at) {
            this.now = at.atZone(SEOUL).toInstant();
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return SEOUL;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static WebSocketSession session(String id, String authorization) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        HttpHeaders headers = new HttpHeaders();
        if (authorization != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getHandshakeHeaders()).thenReturn(headers);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    @DisplayName("우편함: 들어온 순서대로 꺼내고 30초 지난 것은 버린다")
    void mailbox_keeps30Seconds() {
        MovableClock clock = new MovableClock(NOW);
        MessageMailbox mailbox = new MessageMailbox(clock);
        mailbox.put(1, "old");
        clock.advanceSeconds(20);
        mailbox.put(1, "new");
        clock.advanceSeconds(15);
        assertThat(mailbox.drain(1)).containsExactly("new");
        assertThat(mailbox.drain(1)).isEmpty();
        for (int i = 0; i < MessageMailbox.CAPACITY + 5; i++) {
            mailbox.put(2, "m" + i);
        }
        assertThat(mailbox.drain(2)).hasSize(MessageMailbox.CAPACITY).first().isEqualTo("m5");
    }

    @Test
    @DisplayName("WebSocketMessenger: 연결이 없으면 우편함에 두고 연결 뒤 flush로 보낸다")
    void messenger_offlineThenFlush() throws IOException {
        MessageMailbox mailbox = new MessageMailbox(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        SocketSessionRegistry registry = new SocketSessionRegistry();
        WebSocketMessenger messenger =
                new WebSocketMessenger(registry, new RealtimeMessageSerializer(), mailbox);
        messenger.sendAll(List.of(1L), new BattleEnded(9, BattleEnded.Status.FINISHED));

        WebSocketSession socket = session("s1", null);
        registry.register(1, socket);
        messenger.flush(1);
        verify(socket).sendMessage(any(TextMessage.class));
        assertThat(mailbox.drain(1)).isEmpty();
    }

    @Test
    @DisplayName("핸들러: 토큰이 없거나 위조면 정책 위반으로 닫고, 맞으면 등록해 진행값을 LiveBattleService로 넘긴다")
    void handler_authenticatesAndDispatches() throws Exception {
        TokenIssuer tokens = mock(TokenIssuer.class);
        when(tokens.verify("good")).thenReturn(42L);
        when(tokens.verify("bad")).thenThrow(new AuthFailureException("위조"));
        LiveBattleService live = mock(LiveBattleService.class);
        SocketSessionRegistry registry = new SocketSessionRegistry();
        MessageMailbox mailbox = new MessageMailbox(Clock.system(SEOUL));
        RealtimeMessageSerializer serializer = new RealtimeMessageSerializer();
        LiveSocketHandler handler =
                new LiveSocketHandler(
                        tokens,
                        registry,
                        new WebSocketMessenger(registry, serializer, mailbox),
                        serializer,
                        new ProgressDispatcher(live, Clock.system(SEOUL)));

        WebSocketSession anonymous = session("a", null);
        handler.afterConnectionEstablished(anonymous);
        verify(anonymous).close(any(CloseStatus.class));
        WebSocketSession forged = session("f", "Bearer bad");
        handler.afterConnectionEstablished(forged);
        verify(forged).close(any(CloseStatus.class));
        assertThat(registry.find(42)).isEmpty();

        WebSocketSession member = session("m", "Bearer good");
        handler.afterConnectionEstablished(member);
        assertThat(registry.find(42)).isPresent();
        handler.handleTextMessage(member, new TextMessage(PROGRESS));
        verify(live).onProgress(eq(42L), eq(7L), any(), any());

        handler.handleTextMessage(member, new TextMessage("{\"latitude\":37.5}"));
        handler.handleTextMessage(anonymous, new TextMessage(PROGRESS));
        verify(live).onProgress(anyLong(), anyLong(), any(), any());
        handler.afterConnectionClosed(member, CloseStatus.NORMAL);
        assertThat(registry.find(42)).isEmpty();
    }

    @Test
    @DisplayName("PollingMessenger: 보낸 메시지를 drain으로 꺼낸다(C-004)")
    void pollingMessenger_drain() {
        PollingMessenger polling =
                new PollingMessenger(
                        new RealtimeMessageSerializer(), new MessageMailbox(Clock.system(SEOUL)));
        polling.send(3, new BattleEnded(9, BattleEnded.Status.RESULT_PENDING));
        assertThat(polling.drain(3)).singleElement().asString().contains("\"BattleEnded\"");
        assertThat(polling.drain(3)).isEmpty();
    }

    @Test
    @DisplayName("새 연결이 오면 옛 세션을 닫고, 옛 세션의 종료는 새 등록을 지우지 않는다")
    void registry_replacesOldSession() throws IOException {
        SocketSessionRegistry registry = new SocketSessionRegistry();
        WebSocketSession first = session("1", null);
        WebSocketSession second = session("2", null);
        registry.register(5, first);
        registry.register(5, second);
        verify(first).close(any(CloseStatus.class));
        registry.unregister(5, first);
        assertThat(registry.find(5)).isPresent();
        verify(second, never()).close(any(CloseStatus.class));
    }
}
