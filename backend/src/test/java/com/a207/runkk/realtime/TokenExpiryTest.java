package com.a207.runkk.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.battle.BattleStartService;
import com.a207.runkk.battle.LiveBattleService;
import com.a207.runkk.crew.CrewRunLiveService;
import com.a207.runkk.crew.chat.CrewChatService;
import com.a207.runkk.member.AccessGrant;
import com.a207.runkk.member.AuthFailureException;
import com.a207.runkk.member.TokenIssuer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

/**
 * 실시간 토큰 만료 4401(D-110, F.3.3, 5.7.2 닫기 코드). 02-01 2.1.10.19 TC-6194~TC-6197과 03-통합 T-31의 서버
 * 쪽(TC-6771·TC-6772, TC-6779)을 가짜 TaskScheduler·고정 시계로 본다.
 */
@DisplayName("실시간 토큰 만료 4401")
class TokenExpiryTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant T0 = Instant.parse("2026-10-22T01:00:00Z");
    private static final Instant EXPIRES = T0.plusSeconds(3_600);
    private static final String PROGRESS =
            "{\"channel\":\"BATTLE\",\"channelId\":101,\"distanceMeters\":100,"
                    + "\"elapsedSeconds\":30,\"sentAt\":\"2026-10-22T10:59:59\"}";

    private final ManualTaskScheduler scheduler = new ManualTaskScheduler();
    private final SocketSessionRegistry registry = new SocketSessionRegistry(scheduler);

    private static WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getHandshakeHeaders()).thenReturn(new HttpHeaders());
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    @DisplayName("TC-6194 핸드셰이크: 유효 토큰은 세션 속성에 memberId·expiresAt, 만료 토큰은 401로 거부")
    void handshake() {
        TokenIssuer tokens = mock(TokenIssuer.class);
        when(tokens.verifyGrant("good")).thenReturn(new AccessGrant(3, EXPIRES));
        when(tokens.verifyGrant("expired")).thenThrow(new AuthFailureException("만료"));
        TokenHandshakeInterceptor interceptor = new TokenHandshakeInterceptor(tokens);

        Map<String, Object> attributes = new HashMap<>();
        assertThat(handshake(interceptor, "Bearer good", attributes, new MockHttpServletResponse()))
                .isTrue();
        assertThat(attributes)
                .containsEntry(LiveSocketHandler.MEMBER_ID, 3L)
                .containsEntry(LiveSocketHandler.EXPIRES_AT, EXPIRES);

        MockHttpServletResponse rejected = new MockHttpServletResponse();
        Map<String, Object> none = new HashMap<>();
        assertThat(handshake(interceptor, "Bearer expired", none, rejected)).isFalse();
        assertThat(rejected.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(none).isEmpty();
        assertThat(handshake(interceptor, null, none, new MockHttpServletResponse())).isFalse();
    }

    private static boolean handshake(
            TokenHandshakeInterceptor interceptor,
            String authorization,
            Map<String, Object> attributes,
            MockHttpServletResponse response) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws");
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        boolean accepted =
                interceptor.beforeHandshake(
                        new ServletServerHttpRequest(request),
                        new ServletServerHttpResponse(response),
                        mock(WebSocketHandler.class),
                        attributes);
        return accepted;
    }

    @Test
    @DisplayName("TC-6195 등록은 만료 정각에 4401로 닫는 작업 하나를 예약하고, 그 전 unregister는 예약을 거둔다")
    void register_schedulesExpiry_unregisterCancels() throws Exception {
        WebSocketSession s1 = session("s1");
        registry.register(3, s1, EXPIRES);
        assertThat(scheduler.pending()).singleElement().extracting(t -> t.at).isEqualTo(EXPIRES);

        assertThat(scheduler.advanceTo(EXPIRES.minusMillis(1))).isZero();
        verify(s1, never()).close(any(CloseStatus.class));
        assertThat(scheduler.advanceTo(EXPIRES)).isEqualTo(1);
        verify(s1, times(1)).close(SocketSessionRegistry.TOKEN_EXPIRED);
        assertThat(registry.find(3)).isEmpty();

        WebSocketSession s2 = session("s2");
        registry.register(4, s2, EXPIRES);
        registry.unregister(4, s2);
        assertThat(scheduler.pending()).isEmpty();
        assertThat(scheduler.advanceTo(EXPIRES.plusSeconds(60))).isZero();
        verify(s2, never()).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("리뷰 M2: 만료가 이미 지나 예약이 곧바로 돌아도 등록 뒤라 4401로 닫는다")
    void alreadyExpired_closesImmediately() throws Exception {
        ManualTaskScheduler immediate =
                new ManualTaskScheduler() {
                    @Override
                    public java.util.concurrent.ScheduledFuture<?> schedule(
                            Runnable task, Instant startTime) {
                        java.util.concurrent.ScheduledFuture<?> f = super.schedule(task, startTime);
                        advanceTo(startTime);
                        return f;
                    }
                };
        SocketSessionRegistry eager = new SocketSessionRegistry(immediate);
        WebSocketSession s1 = session("s1");

        eager.register(3, s1, T0.minusSeconds(1));

        verify(s1).close(SocketSessionRegistry.TOKEN_EXPIRED);
        assertThat(eager.find(3)).isEmpty();
    }

    @Test
    @DisplayName("TC-6196·TC-6779 같은 회원의 새 연결: 옛 연결은 1000으로 닫고 옛 예약을 거두며 새 예약 하나만 남는다")
    void reconnect_replacesSchedule() throws Exception {
        WebSocketSession s1 = session("s1");
        WebSocketSession s2 = session("s2");
        Instant e2 = EXPIRES.plusSeconds(1_800);
        registry.register(3, s1, EXPIRES);
        registry.register(3, s2, e2);

        verify(s1).close(CloseStatus.NORMAL);
        assertThat(scheduler.pending()).singleElement().extracting(t -> t.at).isEqualTo(e2);
        scheduler.advanceTo(EXPIRES);
        verify(s1, never()).close(SocketSessionRegistry.TOKEN_EXPIRED);
        registry.unregister(3, s1);
        assertThat(registry.find(3)).isPresent();
        assertThat(scheduler.pending()).hasSize(1);

        scheduler.advanceTo(e2);
        verify(s2).close(SocketSessionRegistry.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("TC-6197·TC-6772 만료 1ms 전 진행값은 처리하고, 만료 시각에 받으면 예약이 늦어도 4401로 닫고 처리하지 않는다")
    void handleText_checksExpiryFirst() throws Exception {
        LiveBattleService live = mock(LiveBattleService.class);
        RealtimeMessageSerializer serializer = new RealtimeMessageSerializer();
        RealtimeUnitTest.MovableClock clock =
                new RealtimeUnitTest.MovableClock(
                        java.time.LocalDateTime.ofInstant(EXPIRES.minusMillis(1), SEOUL));
        MessageMailbox mailbox = new MessageMailbox(clock);
        LiveSocketHandler handler =
                new LiveSocketHandler(
                        mock(TokenIssuer.class),
                        registry,
                        new WebSocketMessenger(registry, serializer, mailbox),
                        serializer,
                        new ProgressDispatcher(live, mock(CrewRunLiveService.class), clock),
                        mock(BattleStartService.class),
                        mock(CrewChatService.class),
                        new ChatPresenceRegistry(),
                        clock);
        WebSocketSession member = session("m");
        member.getAttributes().put(LiveSocketHandler.MEMBER_ID, 3L);
        member.getAttributes().put(LiveSocketHandler.EXPIRES_AT, EXPIRES);
        handler.afterConnectionEstablished(member);
        assertThat(scheduler.pending()).hasSize(1);

        handler.handleTextMessage(member, new TextMessage(PROGRESS));
        verify(live, times(1)).onProgress(eq(3L), eq(101L), any(), any());

        clock.advanceMillis(1);
        handler.handleTextMessage(member, new TextMessage(PROGRESS));
        verify(member).close(SocketSessionRegistry.TOKEN_EXPIRED);
        verify(live, times(1)).onProgress(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("인터셉터 없이 연결되면 헤더 토큰의 verifyGrant로 회원·만료 시각을 정하고 예약한다")
    void fallbackAuthentication_usesGrant() throws Exception {
        TokenIssuer tokens = mock(TokenIssuer.class);
        when(tokens.verifyGrant("good")).thenReturn(new AccessGrant(9, EXPIRES));
        RealtimeMessageSerializer serializer = new RealtimeMessageSerializer();
        Clock clock = Clock.fixed(T0, SEOUL);
        LiveSocketHandler handler =
                new LiveSocketHandler(
                        tokens,
                        registry,
                        new WebSocketMessenger(registry, serializer, new MessageMailbox(clock)),
                        serializer,
                        new ProgressDispatcher(
                                mock(LiveBattleService.class),
                                mock(CrewRunLiveService.class),
                                clock),
                        mock(BattleStartService.class),
                        mock(CrewChatService.class),
                        new ChatPresenceRegistry(),
                        clock);
        WebSocketSession member = session("h");
        member.getHandshakeHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer good");

        handler.afterConnectionEstablished(member);

        assertThat(registry.find(9)).isPresent();
        assertThat(member.getAttributes()).containsEntry(LiveSocketHandler.EXPIRES_AT, EXPIRES);
        assertThat(scheduler.pending()).singleElement().extracting(t -> t.at).isEqualTo(EXPIRES);
    }
}
