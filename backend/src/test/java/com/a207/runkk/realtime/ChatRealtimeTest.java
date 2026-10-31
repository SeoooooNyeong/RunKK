package com.a207.runkk.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.battle.BattleStartService;
import com.a207.runkk.battle.LiveBattleService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.crew.CrewRunLiveService;
import com.a207.runkk.crew.chat.CrewChatService;
import com.a207.runkk.crew.chat.live.ChatRemovalReason;
import com.a207.runkk.crew.chat.live.CrewChatRemoved;
import com.a207.runkk.member.TokenIssuer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * realtime의 채팅 입력·방 입장(설계 6.1.13 InboundChatPresence·ChatPresenceRegistry·PollingChatPresence,
 * 5.7.2 채팅 수신 규칙, D-101). TC-6620 단위 부분, TC-6708.
 */
@DisplayName("realtime 채팅 입장·전송")
class ChatRealtimeTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");
    private static final Instant EXPIRES = NOW.plusSeconds(3_600);

    private final CrewChatService chatService = mock(CrewChatService.class);
    private final ChatPresenceRegistry presence = new ChatPresenceRegistry();
    private final RealtimeMessageSerializer serializer = new RealtimeMessageSerializer();
    private final Clock clock = Clock.fixed(NOW, SEOUL);
    private SocketSessionRegistry registry;
    private MessageMailbox mailbox;
    private LiveSocketHandler handler;

    @BeforeEach
    void setUp() {
        registry = new SocketSessionRegistry(new ManualTaskScheduler());
        mailbox = new MessageMailbox(clock);
        handler =
                new LiveSocketHandler(
                        mock(TokenIssuer.class),
                        registry,
                        new WebSocketMessenger(registry, serializer, mailbox),
                        serializer,
                        new ProgressDispatcher(
                                mock(LiveBattleService.class),
                                mock(CrewRunLiveService.class),
                                clock),
                        mock(BattleStartService.class),
                        chatService,
                        presence,
                        clock);
        doThrow(new BusinessRuleException(ReasonCode.NOT_CREW_MEMBER))
                .when(chatService)
                .checkRoomAccess(9L, 7L);
    }

    private static WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getHandshakeHeaders()).thenReturn(new HttpHeaders());
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private WebSocketSession connected(String id, long memberId) throws Exception {
        WebSocketSession s = session(id);
        s.getAttributes().put(LiveSocketHandler.MEMBER_ID, memberId);
        s.getAttributes().put(LiveSocketHandler.EXPIRES_AT, EXPIRES);
        handler.afterConnectionEstablished(s);
        return s;
    }

    private static TextMessage presenceOf(long crewId, boolean present) {
        return new TextMessage(
                "{\"type\":\"ChatPresence\",\"crewId\":"
                        + crewId
                        + ",\"present\":"
                        + present
                        + "}");
    }

    @Test
    @DisplayName("크루원의 ChatPresence(참)는 checkRoomAccess를 거쳐 방에 넣고, 거짓이면 뺀다")
    void presence_enterAndLeave() throws Exception {
        WebSocketSession b = connected("b", 3);
        handler.handleTextMessage(b, presenceOf(7, true));
        verify(chatService).checkRoomAccess(3L, 7L);
        assertThat(presence.presentMembers(7)).containsExactly(3L);

        handler.handleTextMessage(b, presenceOf(7, false));
        assertThat(presence.presentMembers(7)).isEmpty();
    }

    @Test
    @DisplayName("BE5-A-2·D-173 같은 세션이 이미 확인한 방은 퇴장 뒤 재입장해도 다시 확인하지 않고, 거부된 방은 계속 거부한다")
    void presence_checksEachRoomOncePerSession() throws Exception {
        WebSocketSession b = connected("b", 3);
        for (int i = 0; i < 20; i++) {
            handler.handleTextMessage(b, presenceOf(7, true));
            handler.handleTextMessage(b, presenceOf(7, false));
        }
        handler.handleTextMessage(b, presenceOf(7, true));
        verify(chatService, times(1)).checkRoomAccess(3L, 7L);
        assertThat(presence.presentMembers(7)).containsExactly(3L);

        WebSocketSession o = connected("o", 9);
        handler.handleTextMessage(o, presenceOf(7, true));
        handler.handleTextMessage(o, presenceOf(7, true));
        verify(chatService, times(1)).checkRoomAccess(9L, 7L);
        assertThat(presence.presentMembers(7)).containsExactly(3L);
        verify(b, never()).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("BE5-A-2·D-173 한 세션은 서로 다른 방 10개까지만 확인하고 넘는 신호는 버린다")
    void presence_checksAtMostTenRoomsPerSession() throws Exception {
        WebSocketSession b = connected("b", 3);
        for (long crew = 100; crew < 120; crew++) {
            handler.handleTextMessage(b, presenceOf(crew, true));
        }
        verify(chatService, times(ChatPresenceRegistry.MAX_CHECKED_ROOMS))
                .checkRoomAccess(eq(3L), anyLong());
        assertThat(presence.presentMembers(109)).containsExactly(3L);
        assertThat(presence.presentMembers(110)).isEmpty();

        WebSocketSession other = connected("other", 4);
        handler.handleTextMessage(other, presenceOf(110, true));
        verify(chatService).checkRoomAccess(4L, 110L);
    }

    @Test
    @DisplayName("TC-6620·TC-6708 크루 밖 회원의 입장은 기록하지 않고 연결은 유지한다")
    void presence_outsiderIsIgnored() throws Exception {
        WebSocketSession o = connected("o", 9);
        handler.handleTextMessage(o, presenceOf(7, true));
        assertThat(presence.presentMembers(7)).isEmpty();
        verify(o, never()).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("소켓이 닫히면 그 회원을 모든 방에서 지운다. 같은 회원의 새 연결이 먼저 들어왔으면 그 입장은 남긴다")
    void close_clearsPresenceUnlessNewerConnection() throws Exception {
        WebSocketSession first = connected("first", 3);
        handler.handleTextMessage(first, presenceOf(7, true));
        handler.afterConnectionClosed(first, CloseStatus.NORMAL);
        assertThat(presence.presentMembers(7)).isEmpty();

        WebSocketSession old = connected("old", 3);
        WebSocketSession fresh = connected("new", 3);
        handler.handleTextMessage(fresh, presenceOf(7, true));
        handler.afterConnectionClosed(old, CloseStatus.NORMAL);
        assertThat(presence.presentMembers(7)).containsExactly(3L);
    }

    @Test
    @DisplayName("진행값 경로 폴링 입구는 ChatPresence를 받지 않는다(400, F.3.2)")
    void pollingProgress_rejectsChatPresence() {
        LivePollingController polling =
                new LivePollingController(
                        new PollingMessenger(serializer, mailbox),
                        serializer,
                        new ProgressDispatcher(
                                mock(LiveBattleService.class),
                                mock(CrewRunLiveService.class),
                                clock));
        assertThat(
                        polling.progress(
                                        3,
                                        "{\"type\":\"ChatPresence\",\"crewId\":7,\"present\":true}")
                                .getStatusCode()
                                .value())
                .isEqualTo(400);
    }

    @Test
    @DisplayName("채팅 전송은 연결된 회원에게만 보내고 연결이 없으면 버린다(우편함 0, D-101)")
    void webSocketMessenger_chatSkipsMailbox() throws Exception {
        WebSocketSession b = connected("b", 3);
        WebSocketMessenger messenger = new WebSocketMessenger(registry, serializer, mailbox);

        messenger.send(List.of(3L, 4L), new CrewChatRemoved(7, 801, ChatRemovalReason.DELETED));

        verify(b).sendMessage(any(TextMessage.class));
        assertThat(mailbox.drain(4)).isEmpty();
    }

    @Test
    @DisplayName("폴링 대체 모드: 방 입장은 늘 빈 집합, 채팅 전송은 우편함에 넣지 않는다")
    void pollingMode_chatIsNoOp() {
        PollingMessenger polling = new PollingMessenger(serializer, mailbox);
        polling.send(Set.of(3L), new CrewChatRemoved(7, 801, ChatRemovalReason.HIDDEN));
        assertThat(polling.drain(3)).isEmpty();
        assertThat(new PollingChatPresence().presentMembers(7)).isEmpty();
    }

    @Test
    @DisplayName("ChatPresenceRegistry: 여러 방, 빈 방 정리, 돌려준 집합은 사본")
    void registry_basics() {
        presence.enter(7, 1, "s1");
        presence.enter(7, 2, "s2");
        presence.enter(8, 1, "s1");
        Set<Long> snapshot = presence.presentMembers(7);
        presence.leaveAll(1, "old");
        assertThat(presence.presentMembers(8)).containsExactly(1L);
        presence.leaveAll(1, "s1");
        assertThat(snapshot).containsExactlyInAnyOrder(1L, 2L);
        assertThat(presence.presentMembers(7)).containsExactly(2L);
        assertThat(presence.presentMembers(8)).isEmpty();
        presence.leave(7, 2);
        assertThat(presence.presentMembers(7)).isEmpty();
    }
}
