package com.a207.runkk.infra.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.aicoach.CoachChatGateway;
import com.a207.runkk.aicoach.CoachMessage;
import com.a207.runkk.aicoach.CoachQuestion;
import com.a207.runkk.aicoach.CoachRejected;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AiServerCoachGatewayContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MESSAGES = "/coach/users/";
    private static final String META =
            "event: meta\r\ndata: {\"conversationId\":\"conv-1\"}\r\n\r\n";
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void relaysBeforeUpstreamCompletesAndDoesNotExposeToolEvents() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<JsonNode> request = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        CoachChatGateway gateway =
                start(
                        exchange -> {
                            token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
                            request.set(JSON.readTree(exchange.getRequestBody()));
                            exchange.getResponseHeaders()
                                    .set("Content-Type", "text/event-stream; charset=utf-8");
                            exchange.sendResponseHeaders(200, 0);
                            try (var body = exchange.getResponseBody()) {
                                body.write(META.getBytes(StandardCharsets.UTF_8));
                                body.flush();
                                await(started);
                                String rest =
                                        """
                                        : heartbeat

                                        event: tool
                                        data: {"name":"private_tool"}

                                        event: delta
                                        data: {"text":
                                        data: "좋아요"}

                                        event: done
                                        data: {"tokensIn":12}

                                        """;
                                byte[] bytes = rest.getBytes(StandardCharsets.UTF_8);
                                for (byte value : bytes) {
                                    body.write(value);
                                }
                            }
                        });
        Events events = new Events(started);
        try (CoachChatGateway.Stream stream = gateway.open(42, new CoachQuestion("conv-1", "질문"))) {
            stream.read(events);
        }
        assertThat(token.get()).isEqualTo("test-token");
        assertThat(request.get())
                .isEqualTo(
                        JSON.readTree(
                                "{\"userId\":\"42\",\"conversationId\":\"conv-1\",\"message\":\"질문\"}"));
        assertThat(events.values).containsExactly("started:conv-1", "delta:좋아요", "done");
    }

    @ParameterizedTest
    @CsvSource({
        "404,CONVERSATION_EXPIRED",
        "413,QUESTION_TOO_LONG",
        "429,COACH_QUOTA_EXCEEDED",
        "503,RETRY"
    })
    void translatesRejections(int upstreamStatus, String expected) throws Exception {
        CoachChatGateway gateway =
                start(
                        exchange -> {
                            exchange.getRequestBody().readAllBytes();
                            exchange.sendResponseHeaders(upstreamStatus, -1);
                            exchange.close();
                        });
        assertThatThrownBy(() -> gateway.open(42, new CoachQuestion("conv-1", "질문")))
                .isInstanceOf(CoachRejected.class)
                .hasMessage(expected);
    }

    @Test
    void eofAndMismatchedConversationCannotProduceDone() throws Exception {
        CoachChatGateway gateway =
                start(
                        exchange -> {
                            exchange.getRequestBody().readAllBytes();
                            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                            byte[] bytes = META.getBytes(StandardCharsets.UTF_8);
                            exchange.sendResponseHeaders(200, bytes.length);
                            try (var body = exchange.getResponseBody()) {
                                body.write(bytes);
                            }
                        });
        Events eof = new Events(new CountDownLatch(0));
        try (var stream = gateway.open(42, new CoachQuestion(null, "질문"))) {
            assertThatThrownBy(() -> stream.read(eof)).isInstanceOf(IOException.class);
        }
        assertThat(eof.values).containsExactly("started:conv-1");
        Events mismatch = new Events(new CountDownLatch(0));
        try (var stream = gateway.open(42, new CoachQuestion("different", "질문"))) {
            assertThatThrownBy(() -> stream.read(mismatch)).isInstanceOf(IOException.class);
        }
        assertThat(mismatch.values).isEmpty();
    }

    @Test
    void closingAStreamInterruptsPendingUpstreamRead() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        CoachChatGateway gateway =
                start(
                        exchange -> {
                            exchange.getRequestBody().readAllBytes();
                            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                            exchange.sendResponseHeaders(200, 0);
                            try (var body = exchange.getResponseBody()) {
                                body.write(META.getBytes(StandardCharsets.UTF_8));
                                body.flush();
                                await(release);
                            }
                        });
        try (var stream = gateway.open(42, new CoachQuestion(null, "질문"))) {
            var reading =
                    CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    stream.read(new Events(started));
                                    return false;
                                } catch (IOException failure) {
                                    return true;
                                }
                            });
            await(started);
            stream.close();
            assertThat(reading.get(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
        }
    }

    @Test
    void readsPastMessagesInServerZoneWithInternalToken() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        CoachChatGateway gateway =
                startAt(
                        MESSAGES,
                        exchange -> {
                            path.set(exchange.getRequestURI().getPath());
                            token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
                            reply(
                                    exchange,
                                    200,
                                    """
                                    {"conversationId":"conv-1","messages":[
                                    {"role":"user","content":"어제 어땠어?","at":"2026-11-12T00:00:00Z"},
                                    {"role":"assistant","content":"좋았어요.","at":"2026-11-12T00:00:01.5+00:00"}
                                    ]}
                                    """);
                        });

        List<CoachMessage> messages = gateway.messages(42, "conv-1");

        assertThat(path.get()).isEqualTo("/coach/users/42/conversations/conv-1/messages");
        assertThat(token.get()).isEqualTo("test-token");
        assertThat(messages)
                .containsExactly(
                        new CoachMessage(
                                CoachMessage.Role.QUESTION,
                                "어제 어땠어?",
                                LocalDateTime.of(2026, 11, 12, 9, 0)),
                        new CoachMessage(
                                CoachMessage.Role.ANSWER,
                                "좋았어요.",
                                LocalDateTime.of(2026, 11, 12, 9, 0, 1, 500_000_000)));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "404|{\"reason\":\"CONVERSATION_NOT_FOUND\"}|CONVERSATION_EXPIRED",
                "503|{\"reason\":\"UNAVAILABLE\"}|RETRY",
                "200|{\"conversationId\":\"other\",\"messages\":[]}|RETRY",
                "200|{\"conversationId\":\"conv-1\",\"messages\":[{\"role\":\"tool\",\"content\":\"x\",\"at\":\"2026-11-12T00:00:00Z\"}]}|RETRY",
                "200|{\"conversationId\":\"conv-1\",\"messages\":[{\"role\":\"user\",\"content\":\"x\",\"at\":\"어제\"}]}|RETRY",
                "200|[]|RETRY"
            })
    void translatesPastMessageFailures(int upstreamStatus, String body, String expected)
            throws Exception {
        CoachChatGateway gateway =
                startAt(MESSAGES, exchange -> reply(exchange, upstreamStatus, body));

        assertThatThrownBy(() -> gateway.messages(42, "conv-1"))
                .isInstanceOf(CoachRejected.class)
                .hasMessage(expected);
    }

    @Test
    void invalidConversationIdNeverReachesAi() throws Exception {
        CoachChatGateway gateway =
                startAt(
                        MESSAGES,
                        exchange -> {
                            throw new IOException("must not be called");
                        });

        assertThatThrownBy(() -> gateway.messages(42, "../chat"))
                .isInstanceOf(CoachRejected.class)
                .hasMessage("CONVERSATION_EXPIRED");
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private CoachChatGateway start(HttpHandler handler) throws IOException {
        return startAt("/coach/chat", handler);
    }

    private CoachChatGateway startAt(String context, HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(context, handler);
        server.start();
        return new AiServerCoachGateway(
                new AiServerClient.Server(
                        "http://127.0.0.1:" + server.getAddress().getPort(),
                        "test-token",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(15)),
                JSON);
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IOException("test latch expired");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException(failure);
        }
    }

    private static final class Events implements CoachChatGateway.Listener {
        private final CountDownLatch received;
        private final List<String> values = new ArrayList<>();

        private Events(CountDownLatch received) {
            this.received = received;
        }

        @Override
        public void started(String id) {
            values.add("started:" + id);
            received.countDown();
        }

        @Override
        public void delta(String text) {
            values.add("delta:" + text);
        }

        @Override
        public void done() {
            values.add("done");
        }
    }
}
