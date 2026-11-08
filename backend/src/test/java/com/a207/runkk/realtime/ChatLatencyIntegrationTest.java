package com.a207.runkk.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.TestcontainersConfig;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.CrewTag;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
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
 * NFR-040 채팅 3초(부록 F F.1.5, F.11.3, TC-6701): 실 서버 하나(PostgreSQL·Redis 컨테이너)에 같은 채팅방 소켓 10개, 크루원
 * 10명이 메시지 100건을 고르게 보낸다(회원마다 0.3초 간격 10건, 회원당 초당 5건 한도 안). 보낸 시각(POST 직전) → 다른 9개 소켓에서 받은 시각의 차이
 * 900개 중 95% 이상이 3초 이하이고 수신 누락이 0이어야 한다. 같은 JVM의 시계라 기기 시계 차이는 없다. Docker가 없는 PC에서는 건너뛴다.
 */
@SpringBootTest(
        classes = RunkkApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@org.testcontainers.junit.jupiter.Testcontainers(disabledWithoutDocker = true)
@org.junit.jupiter.api.DisplayName("NFR-040 채팅 지연(소켓 10개, 메시지 100건)")
class ChatLatencyIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ChatLatencyIntegrationTest.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MEMBERS = 10;
    private static final int PER_MEMBER = 10;
    private static final long GAP_MILLIS = 300;
    private static final long LIMIT_MILLIS = 3_000;
    private static final double REQUIRED_RATIO = 0.95;

    @LocalServerPort int port;
    @Autowired MemberRepository members;
    @Autowired TokenIssuer tokens;
    @Autowired CrewService crews;
    @Autowired MembershipService membership;
    @Autowired ChatPresenceRegistry presence;
    @Autowired TransactionTemplate tx;

    /** clientMessageId → 받은 시각(밀리초). 회원 소켓마다 하나. */
    static final class Receiver extends TextWebSocketHandler {
        final Map<String, Long> receivedAt = new ConcurrentHashMap<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message)
                throws Exception {
            long now = System.currentTimeMillis();
            JsonNode node = JSON.readTree(message.getPayload());
            if ("CrewChatPosted".equals(node.path("type").asText())) {
                receivedAt.putIfAbsent(node.get("clientMessageId").asText(), now);
            }
        }
    }

    record Sent(int sender, String clientMessageId, long at) {}

    @Test
    @org.junit.jupiter.api.DisplayName("TC-6701 보낸 시각 → 다른 9개 소켓 받은 시각 900개 중 95% 이상 3초 이하, 누락 0")
    void ninetyFivePercentWithinThreeSeconds() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < MEMBERS; i++) {
            String name = "지연러너" + UUID.randomUUID().toString().substring(0, 6);
            ids.add(tx.execute(s -> members.save(MemberFixtures.member(name)).getId()));
        }
        long crewId =
                crews.create(
                                ids.get(0),
                                new CrewCreateCommand(
                                        "지연크루" + UUID.randomUUID().toString().substring(0, 6),
                                        "함께",
                                        Set.of(CrewTag.WEEKDAY_EVENING),
                                        CrewVisibility.PUBLIC,
                                        JoinPolicy.FREE,
                                        null))
                        .crewId();
        ids.subList(1, MEMBERS).forEach(m -> membership.apply(m, JoinTarget.byCrew(crewId)));

        List<Receiver> receivers = new ArrayList<>();
        List<WebSocketSession> sockets = new ArrayList<>();
        List<String> tokenOf = new ArrayList<>();
        for (long m : ids) {
            String token = tokens.issue(m).accessToken();
            tokenOf.add(token);
            Receiver receiver = new Receiver();
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            WebSocketSession socket =
                    new StandardWebSocketClient()
                            .execute(
                                    receiver, headers, URI.create("ws://localhost:" + port + "/ws"))
                            .get(5, TimeUnit.SECONDS);
            socket.sendMessage(
                    new TextMessage(
                            "{\"type\":\"ChatPresence\",\"crewId\":"
                                    + crewId
                                    + ",\"present\":true}"));
            receivers.add(receiver);
            sockets.add(socket);
        }
        long deadline = System.currentTimeMillis() + 5_000;
        while (presence.presentMembers(crewId).size() < MEMBERS
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(presence.presentMembers(crewId)).hasSize(MEMBERS);

        HttpClient http = HttpClient.newHttpClient();
        URI uri =
                URI.create(
                        "http://localhost:" + port + "/api/v1/crews/" + crewId + "/chat/messages");
        List<Sent> sent = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(MEMBERS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> senders = new ArrayList<>();
        for (int s = 0; s < MEMBERS; s++) {
            int sender = s;
            senders.add(
                    pool.submit(
                            () -> {
                                start.await();
                                Thread.sleep(sender * GAP_MILLIS / MEMBERS); // 고르게 흩어 보낸다
                                for (int k = 0; k < PER_MEMBER; k++) {
                                    String clientMessageId = UUID.randomUUID().toString();
                                    String body =
                                            JSON.writeValueAsString(
                                                    Map.of(
                                                            "clientMessageId",
                                                            clientMessageId,
                                                            "content",
                                                            "메시지 " + sender + "-" + k));
                                    long at = System.currentTimeMillis();
                                    HttpResponse<String> response =
                                            http.send(
                                                    HttpRequest.newBuilder(uri)
                                                            .header(
                                                                    HttpHeaders.AUTHORIZATION,
                                                                    "Bearer " + tokenOf.get(sender))
                                                            .header(
                                                                    HttpHeaders.CONTENT_TYPE,
                                                                    "application/json")
                                                            .POST(
                                                                    HttpRequest.BodyPublishers
                                                                            .ofString(body))
                                                            .build(),
                                                    HttpResponse.BodyHandlers.ofString());
                                    assertThat(response.statusCode()).isEqualTo(201);
                                    sent.add(new Sent(sender, clientMessageId, at));
                                    Thread.sleep(GAP_MILLIS);
                                }
                                return null;
                            }));
        }
        start.countDown();
        for (Future<?> f : senders) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(sent).hasSize(MEMBERS * PER_MEMBER);

        long settle = System.currentTimeMillis() + LIMIT_MILLIS * 2;
        while (System.currentTimeMillis() < settle && missing(sent, receivers) > 0) {
            Thread.sleep(50);
        }

        List<Long> latencies = new ArrayList<>();
        for (Sent s : sent) {
            for (int r = 0; r < MEMBERS; r++) {
                if (r == s.sender()) {
                    continue;
                }
                Long got = receivers.get(r).receivedAt.get(s.clientMessageId());
                latencies.add(got == null ? Long.MAX_VALUE : got - s.at());
            }
        }
        Collections.sort(latencies);
        long within = latencies.stream().filter(l -> l <= LIMIT_MILLIS).count();
        long p95 = latencies.get((int) Math.ceil(latencies.size() * REQUIRED_RATIO) - 1);
        log.info(
                "nfr040 samples={} within3s={} p50={}ms p95={}ms max={}ms missing={}",
                latencies.size(),
                within,
                latencies.get(latencies.size() / 2),
                p95,
                latencies.get(latencies.size() - 1),
                missing(sent, receivers));
        assertThat(latencies).hasSize(MEMBERS * PER_MEMBER * (MEMBERS - 1));
        assertThat(missing(sent, receivers)).isZero();
        assertThat(within)
                .isGreaterThanOrEqualTo((long) Math.ceil(latencies.size() * REQUIRED_RATIO));
        for (WebSocketSession socket : sockets) {
            socket.close(CloseStatus.NORMAL);
        }
    }

    private static long missing(List<Sent> sent, List<Receiver> receivers) {
        long missing = 0;
        synchronized (sent) {
            for (Sent s : sent) {
                for (int r = 0; r < receivers.size(); r++) {
                    if (r != s.sender()
                            && !receivers.get(r).receivedAt.containsKey(s.clientMessageId())) {
                        missing++;
                    }
                }
            }
        }
        return missing;
    }
}
