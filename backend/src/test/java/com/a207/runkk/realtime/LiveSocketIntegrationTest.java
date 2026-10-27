package com.a207.runkk.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.battle.Battle;
import com.a207.runkk.battle.BattleRepository;
import com.a207.runkk.battle.BattleStartService;
import com.a207.runkk.battle.MatchTicket;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.crew.CrewLiveSessionStore;
import com.a207.runkk.crew.CrewRunLiveSession;
import com.a207.runkk.member.AccessGrant;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.member.TokenIssuer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket 통합(5.7.2, SQ-04): 실제 서버 /ws에 접속해 핸드셰이크 Authorization 토큰 인증, 진행값 → 상대에게 OpponentProgress
 * 중계, 인증 실패 정책 위반 종료를 확인한다. STOMP 없이 type 필드로 나눈다. 앱 실기기 부분은 제외한다(T-2).
 */
@SpringBootTest(
        classes = RunkkApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@org.junit.jupiter.api.DisplayName("WebSocket /ws 통합")
class LiveSocketIntegrationTest {

    private static final long WAIT_SECONDS = 5;
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort int port;
    @Autowired MemberRepository members;
    @MockitoSpyBean TokenIssuer tokens;
    @Autowired BattleRepository battles;
    @Autowired BattleStartService startService;
    @Autowired TransactionTemplate tx;
    @Autowired CrewLiveSessionStore crewSessions;

    /** 받은 메시지·닫힘을 줄에 넣는 클라이언트. */
    static final class Inbox extends TextWebSocketHandler {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final BlockingQueue<CloseStatus> closes = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closes.add(status);
        }

        JsonNode next(String type) throws Exception {
            while (true) {
                String json = messages.poll(WAIT_SECONDS, TimeUnit.SECONDS);
                assertThat(json).as(type + " 메시지").isNotNull();
                JsonNode node = JSON.readTree(json);
                if (node.get("type").asText().equals(type)) {
                    return node;
                }
            }
        }
    }

    private WebSocketSession connect(Inbox inbox, String token) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (token != null) {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return new StandardWebSocketClient()
                .execute(inbox, headers, URI.create("ws://localhost:" + port + "/ws"))
                .get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private long member(String nickname) {
        return tx.execute(s -> members.save(MemberFixtures.member(nickname)).getId());
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "인증된 두 러너: 한쪽 진행값이 상대에게 OpponentProgress로 가고 위치 항목이 없다, 토큰 없으면 핸드셰이크 401")
    void relayOverWebSocket() throws Exception {
        long alice = member("소켓곰");
        long bob = member("소켓여우");
        LocalDateTime now = LocalDateTime.now().withNano(0);
        MatchTicket a = MatchTicket.open(alice, Tier.GOLD, StandardDistance.KM1, now);
        MatchTicket b = MatchTicket.open(bob, Tier.GOLD, StandardDistance.KM1, now);
        a.assign();
        b.assign();
        long battleId =
                tx.execute(
                        s -> {
                            Battle battle = Battle.random(a, b, now);
                            battle.scheduleStart(now);
                            return battles.save(battle).getId();
                        });
        startService.startDue(now);

        Inbox aliceInbox = new Inbox();
        Inbox bobInbox = new Inbox();
        WebSocketSession aliceSocket = connect(aliceInbox, tokens.issue(alice).accessToken());
        connect(bobInbox, tokens.issue(bob).accessToken());
        aliceSocket.sendMessage(
                new TextMessage(
                        "{\"channel\":\"BATTLE\",\"channelId\":"
                                + battleId
                                + ",\"distanceMeters\":120,\"elapsedSeconds\":40,\"sentAt\":\""
                                + now.plusSeconds(40)
                                + "\"}"));

        JsonNode progress = bobInbox.next("OpponentProgress");
        assertThat(progress.get("battleId").asLong()).isEqualTo(battleId);
        assertThat(progress.get("opponentName").asText()).isEqualTo("소켓곰");
        assertThat(progress.get("opponentDistance").asLong()).isEqualTo(120);
        assertThat(progress.get("connection").asText()).isEqualTo("CONNECTED");
        assertThat(progress.has("latitude")).isFalse();

        Inbox anonymous = new Inbox();
        assertThatThrownBy(() -> connect(anonymous, null)).hasStackTraceContaining("401");
        assertThatThrownBy(() -> connect(new Inbox(), "forged")).hasStackTraceContaining("401");
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "크루런 진행값(CREW_RUN)이 다른 참가자에게 CrewRunRelay로 가고 위치 항목이 없다(5.7.2, FR-149)")
    void crewRunRelayOverWebSocket() throws Exception {
        long alice = member("크루곰");
        long bob = member("크루여우");
        long postId = 9_000 + alice;
        LocalDateTime now = LocalDateTime.now().withNano(0);
        crewSessions.save(
                CrewRunLiveSession.open(
                        postId,
                        StandardDistance.KM1,
                        java.util.Map.of(alice, DisplayName.of("크루곰"), bob, DisplayName.of("크루여우")),
                        now));

        Inbox aliceInbox = new Inbox();
        Inbox bobInbox = new Inbox();
        WebSocketSession aliceSocket = connect(aliceInbox, tokens.issue(alice).accessToken());
        connect(bobInbox, tokens.issue(bob).accessToken());
        aliceSocket.sendMessage(
                new TextMessage(
                        "{\"channel\":\"CREW_RUN\",\"channelId\":"
                                + postId
                                + ",\"distanceMeters\":300,\"elapsedSeconds\":90,\"sentAt\":\""
                                + now.plusSeconds(90)
                                + "\"}"));

        JsonNode relay = bobInbox.next("CrewRunRelay");
        assertThat(relay.get("postId").asLong()).isEqualTo(postId);
        assertThat(relay.get("entries").get(0).get("name").asText()).isEqualTo("크루곰");
        assertThat(relay.get("entries").get(0).get("distance").asLong()).isEqualTo(300);
        assertThat(relay.get("entries").get(0).get("rank").asInt()).isEqualTo(1);
        // D-161: 받는 사람(bob) 줄에만 mine 참
        assertThat(relay.get("entries").get(0).get("mine").asBoolean()).isFalse();
        assertThat(relay.get("entries").get(1).get("memberId").asLong()).isEqualTo(bob);
        assertThat(relay.get("entries").get(1).get("mine").asBoolean()).isTrue();
        assertThat(relay.toString()).doesNotContain("latitude", "longitude");
        crewSessions.delete(postId);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "T-31 TC-6771·TC-6779: 같은 회원의 새 연결은 옛 연결을 1000으로 닫고, 토큰 만료 시각에 서버가 4401 TOKEN_EXPIRED로"
                    + " 닫는다")
    void tokenExpiry_closesWith4401() throws Exception {
        long runner = member("만료곰");
        String token = tokens.issue(runner).accessToken();
        Inbox first = new Inbox();
        connect(first, token);
        org.mockito.Mockito.doReturn(new AccessGrant(runner, Instant.now().plusMillis(1_500)))
                .when(tokens)
                .verifyGrant(token);
        Inbox second = new Inbox();
        connect(second, token);

        CloseStatus replaced = first.closes.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(replaced).isNotNull();
        assertThat(replaced.getCode()).isEqualTo(CloseStatus.NORMAL.getCode());
        CloseStatus expired = second.closes.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(expired).as("만료 시각의 닫힘").isNotNull();
        assertThat(expired.getCode()).isEqualTo(4401);
        assertThat(expired.getReason()).isEqualTo("TOKEN_EXPIRED");
    }
}
