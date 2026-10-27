package com.a207.runkk.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.crew.CrewCreateCommand;
import com.a207.runkk.crew.CrewService;
import com.a207.runkk.crew.CrewVisibility;
import com.a207.runkk.crew.JoinPolicy;
import com.a207.runkk.crew.JoinTarget;
import com.a207.runkk.crew.MembershipService;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.member.TokenIssuer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 실제 /ws와 REST로 채팅 받기(5.7.2, F.3.2, D-101): ChatPresence로 방에 들어간 크루원이 POST한 메시지를 CrewChatPosted로
 * 받고, 크루 밖 소켓은 입장이 기록되지 않으며, 끊긴 동안의 메시지는 우편함이 아니라 ?after=로 메운다. TC-6620, TC-6695, TC-6702, TC-6708.
 */
@SpringBootTest(
        classes = RunkkApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@org.junit.jupiter.api.DisplayName("채팅 WebSocket·REST 통합")
class ChatSocketIntegrationTest {

    private static final long WAIT_SECONDS = 5;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final Set<String> LOCATION_KEYS =
            Set.of("latitude", "longitude", "lat", "lng", "lon", "course", "route", "points");

    @LocalServerPort int port;
    @Autowired MemberRepository members;
    @Autowired TokenIssuer tokens;
    @Autowired CrewService crews;
    @Autowired MembershipService membership;
    @Autowired ChatPresenceRegistry presence;
    @Autowired TransactionTemplate tx;

    private final HttpClient http = HttpClient.newHttpClient();

    /** 받은 메시지를 줄에 넣는 클라이언트. */
    static final class Inbox extends TextWebSocketHandler {
        final BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message)
                throws Exception {
            messages.add(JSON.readTree(message.getPayload()));
        }

        JsonNode next(String type) throws InterruptedException {
            while (true) {
                JsonNode node = messages.poll(WAIT_SECONDS, TimeUnit.SECONDS);
                assertThat(node).as(type + " 메시지").isNotNull();
                if (node.get("type").asText().equals(type)) {
                    return node;
                }
            }
        }
    }

    private long member() {
        String name = "소켓채팅" + NAMES.incrementAndGet();
        return tx.execute(s -> members.save(MemberFixtures.member(name)).getId());
    }

    private String token(long memberId) {
        return tokens.issue(memberId).accessToken();
    }

    private long crewOf(long leader, long... others) {
        long crewId =
                crews.create(
                                leader,
                                new CrewCreateCommand(
                                        "소켓크루" + NAMES.incrementAndGet(),
                                        "함께",
                                        Set.of(PersonalityTag.SOCIAL_RUN),
                                        CrewVisibility.PUBLIC,
                                        JoinPolicy.FREE,
                                        null))
                        .crewId();
        for (long m : others) {
            membership.apply(m, JoinTarget.byCrew(crewId));
        }
        return crewId;
    }

    private WebSocketSession connect(Inbox inbox, long memberId) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token(memberId));
        return new StandardWebSocketClient()
                .execute(inbox, headers, URI.create("ws://localhost:" + port + "/ws"))
                .get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private void enterRoom(WebSocketSession socket, long crewId, long memberId) throws Exception {
        socket.sendMessage(
                new TextMessage(
                        "{\"type\":\"ChatPresence\",\"crewId\":" + crewId + ",\"present\":true}"));
        awaitPresence(crewId, memberId, true);
    }

    private void awaitPresence(long crewId, long memberId, boolean expected)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1_000;
        while (presence.presentMembers(crewId).contains(memberId) != expected
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(presence.presentMembers(crewId).contains(memberId)).isEqualTo(expected);
    }

    private HttpResponse<String> send(long memberId, long crewId, String content) throws Exception {
        String body =
                JSON.writeValueAsString(
                        Map.of(
                                "clientMessageId",
                                UUID.randomUUID().toString(),
                                "content",
                                content));
        return http.send(
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://localhost:"
                                                + port
                                                + "/api/v1/crews/"
                                                + crewId
                                                + "/chat/messages"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(memberId))
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode history(long memberId, long crewId, String query) throws Exception {
        HttpResponse<String> response =
                http.send(
                        HttpRequest.newBuilder(
                                        URI.create(
                                                "http://localhost:"
                                                        + port
                                                        + "/api/v1/crews/"
                                                        + crewId
                                                        + "/chat/messages"
                                                        + query))
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(memberId))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private static List<String> keysOf(JsonNode node) {
        List<String> keys = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            keys.add(field.getKey().toLowerCase());
            if (field.getValue().isContainerNode()) {
                keys.addAll(keysOf(field.getValue()));
            }
        }
        return keys;
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6620·TC-6708·TC-6702 방 안 크루원은 CrewChatPosted를 받고(위치 키 0), 크루 밖 소켓은 입장 기록 0·수신 0,"
                    + " 닫으면 방에서 빠진다")
    void roomMembersReceive_outsiderIgnored() throws Exception {
        long a = member();
        long b = member();
        long outsider = member();
        long crewId = crewOf(a, b);
        Inbox aInbox = new Inbox();
        Inbox bInbox = new Inbox();
        Inbox oInbox = new Inbox();
        WebSocketSession aSocket = connect(aInbox, a);
        WebSocketSession bSocket = connect(bInbox, b);
        WebSocketSession oSocket = connect(oInbox, outsider);
        enterRoom(aSocket, crewId, a);
        enterRoom(bSocket, crewId, b);
        oSocket.sendMessage(
                new TextMessage(
                        "{\"type\":\"ChatPresence\",\"crewId\":" + crewId + ",\"present\":true}"));

        HttpResponse<String> posted = send(a, crewId, "내일 7시 출발");
        assertThat(posted.statusCode()).isEqualTo(201);
        long messageId = JSON.readTree(posted.body()).get("messageId").asLong();

        JsonNode received = bInbox.next("CrewChatPosted");
        assertThat(received.get("messageId").asLong()).isEqualTo(messageId);
        assertThat(received.get("crewId").asLong()).isEqualTo(crewId);
        assertThat(received.get("authorId").asLong()).isEqualTo(a);
        assertThat(received.get("content").asText()).isEqualTo("내일 7시 출발");
        assertThat(received.get("sentAt").asText()).endsWith("+09:00");
        assertThat(keysOf(received)).noneMatch(LOCATION_KEYS::contains);
        assertThat(aInbox.next("CrewChatPosted").get("messageId").asLong()).isEqualTo(messageId);
        assertThat(presence.presentMembers(crewId)).doesNotContain(outsider);
        assertThat(oInbox.messages.poll(300, TimeUnit.MILLISECONDS)).isNull();
        assertThat(oSocket.isOpen()).isTrue();

        bSocket.close(CloseStatus.NORMAL);
        awaitPresence(crewId, b, false);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6695 끊긴 동안 보낸 3건은 우편함으로 오지 않고, 재연결 뒤 ?after=로 오래된 → 새 순 3건을 메운다")
    void reconnect_fillsGapWithAfter() throws Exception {
        long a = member();
        long b = member();
        long crewId = crewOf(a, b);
        WebSocketSession bSocket = connect(new Inbox(), b);
        enterRoom(bSocket, crewId, b);
        long last = JSON.readTree(send(a, crewId, "끊기 전").body()).get("messageId").asLong();
        bSocket.close(CloseStatus.NORMAL);
        awaitPresence(crewId, b, false);

        for (int i = 1; i <= 3; i++) {
            assertThat(send(a, crewId, "끊긴 동안 " + i).statusCode()).isEqualTo(201);
        }

        Inbox again = new Inbox();
        WebSocketSession reconnected = connect(again, b);
        enterRoom(reconnected, crewId, b);
        assertThat(again.messages.poll(300, TimeUnit.MILLISECONDS)).isNull();
        JsonNode filled = history(b, crewId, "?after=" + last);
        assertThat(filled.get("messages")).hasSize(3);
        assertThat(filled.get("hasMore").asBoolean()).isFalse();
        assertThat(filled.get("messages").get(0).get("content").asText()).isEqualTo("끊긴 동안 1");
        assertThat(filled.get("messages").get(2).get("content").asText()).isEqualTo("끊긴 동안 3");
        reconnected.close(CloseStatus.NORMAL);
    }
}
