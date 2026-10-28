package com.a207.runkk.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.battle.BattleStartService;
import com.a207.runkk.battle.LiveBattleService;
import com.a207.runkk.battle.live.BattleEnded;
import com.a207.runkk.crew.CrewRunLiveService;
import com.a207.runkk.crew.RelayEntry;
import com.a207.runkk.crew.chat.CrewChatService;
import com.a207.runkk.member.AccessGrant;
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
import org.mockito.InOrder;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** realtime 단위: 우편함 30초, 연결 없을 때 보관·재연결 때 전달, 핸드셰이크 인증, 진행값 전달, 폴링 우편함. */
@DisplayName("realtime 메신저·핸들러")
class RealtimeUnitTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 19, 0);
    private static final Instant EXPIRES = NOW.plusHours(1).atZone(SEOUL).toInstant();
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

        void advanceMillis(long millis) {
            now = now.plusMillis(millis);
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
        SocketSessionRegistry registry = new SocketSessionRegistry(new ManualTaskScheduler());
        WebSocketMessenger messenger =
                new WebSocketMessenger(registry, new RealtimeMessageSerializer(), mailbox);
        messenger.sendAll(List.of(1L), new BattleEnded(9, BattleEnded.Status.FINISHED));

        WebSocketSession socket = session("s1", null);
        registry.register(1, socket, EXPIRES);
        messenger.flush(1);
        verify(socket).sendMessage(any(TextMessage.class));
        assertThat(mailbox.drain(1)).isEmpty();
    }

    @Test
    @DisplayName(
            "핸들러: 토큰이 없거나 위조면 정책 위반으로 닫고, 맞으면 등록해 우편함 뒤 BattleStart를 다시 보내고 진행값을"
                    + " LiveBattleService로 넘긴다")
    void handler_authenticatesAndDispatches() throws Exception {
        TokenIssuer tokens = mock(TokenIssuer.class);
        when(tokens.verifyGrant("good")).thenReturn(new AccessGrant(42L, EXPIRES));
        when(tokens.verifyGrant("bad")).thenThrow(new AuthFailureException("위조"));
        LiveBattleService live = mock(LiveBattleService.class);
        CrewRunLiveService crewLive = mock(CrewRunLiveService.class);
        BattleStartService starts = mock(BattleStartService.class);
        SocketSessionRegistry registry = new SocketSessionRegistry(new ManualTaskScheduler());
        MessageMailbox mailbox = new MessageMailbox(Clock.system(SEOUL));
        RealtimeMessageSerializer serializer = new RealtimeMessageSerializer();
        LiveSocketHandler handler =
                new LiveSocketHandler(
                        tokens,
                        registry,
                        new WebSocketMessenger(registry, serializer, mailbox),
                        serializer,
                        new ProgressDispatcher(live, crewLive, Clock.system(SEOUL)),
                        starts,
                        mock(CrewChatService.class),
                        new ChatPresenceRegistry(),
                        Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));

        WebSocketSession anonymous = session("a", null);
        handler.afterConnectionEstablished(anonymous);
        verify(anonymous).close(any(CloseStatus.class));
        WebSocketSession forged = session("f", "Bearer bad");
        handler.afterConnectionEstablished(forged);
        verify(forged).close(any(CloseStatus.class));
        assertThat(registry.find(42)).isEmpty();
        verify(starts, never()).resendStart(anyLong());

        mailbox.put(42, "{\"type\":\"MatchFound\"}");
        WebSocketSession member = session("m", "Bearer good");
        doThrow(new IllegalStateException("db")).when(starts).resendStart(42L);
        handler.afterConnectionEstablished(member);
        assertThat(registry.find(42)).isPresent();
        // 우편함을 먼저 비우고 출발 예약 대결의 BattleStart를 다시 보낸다. 실패해도 연결은 유지한다(배틀런 QA 1·2번)
        InOrder replay = inOrder(member, starts);
        replay.verify(member).sendMessage(any(TextMessage.class));
        replay.verify(starts).resendStart(42L);
        verify(member, never()).close(any(CloseStatus.class));
        handler.handleTextMessage(member, new TextMessage(PROGRESS));
        verify(live).onProgress(eq(42L), eq(7L), any(), any());
        handler.handleTextMessage(
                member, new TextMessage(PROGRESS.replace("\"BATTLE\"", "\"CREW_RUN\"")));
        verify(crewLive).onProgress(eq(42L), eq(7L), any(), any());

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

    private static com.a207.runkk.crew.live.CrewRunRelay relayOf(long... memberIds) {
        List<RelayEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < memberIds.length; i++) {
            entries.add(
                    new RelayEntry(
                            com.a207.runkk.common.DisplayName.of("러너" + memberIds[i]),
                            com.a207.runkk.common.Distance.ofMeters(100L * (memberIds.length - i)),
                            i + 1,
                            RelayEntry.Connection.CONNECTED,
                            memberIds[i],
                            false));
        }
        return new com.a207.runkk.crew.live.CrewRunRelay(77, entries);
    }

    private static List<Long> mineLines(String json) throws IOException {
        List<Long> mine = new java.util.ArrayList<>();
        new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(json)
                .path("entries")
                .forEach(
                        e -> {
                            if (e.path("mine").asBoolean()) {
                                mine.add(e.path("memberId").asLong());
                            }
                        });
        return mine;
    }

    @Test
    @DisplayName("크루런 중계(D-161): 폴링·WebSocket 모두 받는 사람마다 자기 줄에만 mine true")
    void crewRunRelay_isPersonalizedPerRecipient() throws IOException {
        PollingMessenger polling =
                new PollingMessenger(
                        new RealtimeMessageSerializer(), new MessageMailbox(Clock.system(SEOUL)));
        polling.sendAll(List.of(11L, 12L, 13L), relayOf(11, 12, 13));
        polling.send(12L, relayOf(11, 12, 13));
        assertThat(mineLines(polling.drain(11).get(0))).containsExactly(11L);
        assertThat(polling.drain(12))
                .hasSize(2)
                .allSatisfy(j -> assertThat(mineLines(j)).containsExactly(12L));
        assertThat(mineLines(polling.drain(13).get(0))).containsExactly(13L);

        MessageMailbox mailbox = new MessageMailbox(Clock.system(SEOUL));
        SocketSessionRegistry registry = new SocketSessionRegistry(new ManualTaskScheduler());
        WebSocketMessenger messenger =
                new WebSocketMessenger(registry, new RealtimeMessageSerializer(), mailbox);
        WebSocketSession online = session("s11", null);
        registry.register(11, online, EXPIRES);
        messenger.sendAll(List.of(11L, 12L), relayOf(11, 12));
        org.mockito.ArgumentCaptor<TextMessage> sent =
                org.mockito.ArgumentCaptor.forClass(TextMessage.class);
        verify(online).sendMessage(sent.capture());
        assertThat(mineLines(sent.getValue().getPayload())).containsExactly(11L);
        assertThat(mineLines(mailbox.drain(12).get(0))).containsExactly(12L);
    }

    @Test
    @DisplayName("L22: 폴링 진행값 본문이 WebSocket 상한(1024바이트)을 넘으면 읽지 않고 400")
    void pollingProgress_rejectsOversizedBody() {
        LiveBattleService live = mock(LiveBattleService.class);
        CrewRunLiveService crewLive = mock(CrewRunLiveService.class);
        LivePollingController controller =
                new LivePollingController(
                        new PollingMessenger(
                                new RealtimeMessageSerializer(),
                                new MessageMailbox(Clock.system(SEOUL))),
                        new RealtimeMessageSerializer(),
                        new ProgressDispatcher(live, crewLive, Clock.system(SEOUL)));
        String padded = PROGRESS.replace("}", ",\"pad\":\"" + "x".repeat(1_100) + "\"}");
        assertThat(controller.progress(42L, padded).getStatusCode().value()).isEqualTo(400);
        verify(live, never()).onProgress(anyLong(), anyLong(), any(), any());
        assertThat(controller.progress(42L, PROGRESS).getStatusCode().value()).isEqualTo(204);
    }

    @Test
    @DisplayName("새 연결이 오면 옛 세션을 닫고, 옛 세션의 종료는 새 등록을 지우지 않는다")
    void registry_replacesOldSession() throws IOException {
        SocketSessionRegistry registry = new SocketSessionRegistry(new ManualTaskScheduler());
        WebSocketSession first = session("1", null);
        WebSocketSession second = session("2", null);
        registry.register(5, first, EXPIRES);
        registry.register(5, second, EXPIRES);
        verify(first).close(any(CloseStatus.class));
        registry.unregister(5, first);
        assertThat(registry.find(5)).isPresent();
        verify(second, never()).close(any(CloseStatus.class));
    }
}
