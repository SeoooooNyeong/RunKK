package com.a207.runkk.infra.integration.ai;

import com.a207.runkk.aicoach.CoachChatGateway;
import com.a207.runkk.aicoach.CoachMessage;
import com.a207.runkk.aicoach.CoachQuestion;
import com.a207.runkk.aicoach.CoachRejected;
import com.a207.runkk.common.ServerZone;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AiServerCoachGateway implements CoachChatGateway {
    private static final int MAX_FRAME_CHARS = 64 * 1024;
    // ai는 최근 200건까지만 돌려준다. 답이 길어도 넉넉한 상한을 두고 넘으면 읽지 않는다.
    private static final int MAX_MESSAGES_BYTES = 2 * 1024 * 1024;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(AiServerClient.CONNECT_TIMEOUT).build();
    private final AiServerClient.Server server;
    private final ObjectMapper json;
    private final ObjectReader events;

    public AiServerCoachGateway(AiServerClient.Server server, ObjectMapper json) {
        this.server = server;
        this.json = json;
        this.events = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @Override
    public Stream open(long memberId, CoachQuestion question) {
        try {
            Map<String, String> body =
                    question.conversationId() == null
                            ? Map.of("userId", Long.toString(memberId), "message", question.text())
                            : Map.of(
                                    "userId",
                                    Long.toString(memberId),
                                    "message",
                                    question.text(),
                                    "conversationId",
                                    question.conversationId());
            HttpRequest request =
                    HttpRequest.newBuilder(
                                    URI.create(
                                            server.serverUrl().replaceFirst("/+$", "")
                                                    + "/coach/chat"))
                            .header(AiServerClient.TOKEN_HEADER, server.internalToken())
                            .header("Content-Type", "application/json")
                            .header("Accept", "text/event-stream")
                            .timeout(Duration.ofSeconds(5))
                            .POST(
                                    HttpRequest.BodyPublishers.ofByteArray(
                                            json.writeValueAsBytes(body)))
                            .build();
            HttpResponse<InputStream> response =
                    http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String media = response.headers().firstValue("Content-Type").orElse("");
            if (response.statusCode() == 200
                    && media.split(";", 2)[0]
                            .strip()
                            .toLowerCase(Locale.ROOT)
                            .equals("text/event-stream")) {
                return new EventStream(response.body(), question.conversationId());
            }
            try (InputStream ignored = response.body()) {
                throw new CoachRejected(
                        switch (response.statusCode()) {
                            case 404 -> CoachRejected.Reason.CONVERSATION_EXPIRED;
                            case 413 -> CoachRejected.Reason.QUESTION_TOO_LONG;
                            case 429 -> CoachRejected.Reason.COACH_QUOTA_EXCEEDED;
                            default -> CoachRejected.Reason.RETRY;
                        });
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new CoachRejected(CoachRejected.Reason.RETRY);
        } catch (IOException failure) {
            throw new CoachRejected(CoachRejected.Reason.RETRY);
        }
    }

    @Override
    public List<CoachMessage> messages(long memberId, String conversationId) {
        if (!CoachQuestion.validId(conversationId)) {
            throw new CoachRejected(CoachRejected.Reason.CONVERSATION_EXPIRED);
        }
        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        server.serverUrl().replaceFirst("/+$", "")
                                                + "/coach/users/"
                                                + memberId
                                                + "/conversations/"
                                                + conversationId
                                                + "/messages"))
                        .header(AiServerClient.TOKEN_HEADER, server.internalToken())
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
        try {
            HttpResponse<InputStream> response =
                    http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() == 404) {
                    throw new CoachRejected(CoachRejected.Reason.CONVERSATION_EXPIRED);
                }
                if (response.statusCode() != 200) {
                    throw new CoachRejected(CoachRejected.Reason.RETRY);
                }
                byte[] bytes = body.readNBytes(MAX_MESSAGES_BYTES + 1);
                if (bytes.length > MAX_MESSAGES_BYTES) {
                    throw new IOException("coach messages too large");
                }
                return parseMessages(events.readTree(bytes), conversationId);
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new CoachRejected(CoachRejected.Reason.RETRY);
        } catch (IOException failure) {
            throw new CoachRejected(CoachRejected.Reason.RETRY);
        }
    }

    private static List<CoachMessage> parseMessages(JsonNode body, String conversationId)
            throws IOException {
        if (body == null
                || !body.isObject()
                || !conversationId.equals(body.path("conversationId").textValue())
                || !body.path("messages").isArray()) {
            throw new IOException("invalid coach messages");
        }
        List<CoachMessage> messages = new ArrayList<>();
        for (JsonNode item : body.get("messages")) {
            JsonNode content = item.get("content");
            JsonNode at = item.get("at");
            if (content == null || !content.isTextual() || at == null || !at.isTextual()) {
                throw new IOException("invalid coach message");
            }
            CoachMessage.Role role =
                    switch (item.path("role").asText()) {
                        case "user" -> CoachMessage.Role.QUESTION;
                        case "assistant" -> CoachMessage.Role.ANSWER;
                        default -> throw new IOException("invalid coach message role");
                    };
            try {
                messages.add(
                        new CoachMessage(
                                role,
                                content.textValue(),
                                OffsetDateTime.parse(at.textValue())
                                        .atZoneSameInstant(ServerZone.ID)
                                        .toLocalDateTime()));
            } catch (DateTimeParseException invalid) {
                throw new IOException("invalid coach message time", invalid);
            }
        }
        return List.copyOf(messages);
    }

    private final class EventStream implements Stream {
        private final InputStream body;
        private final String requestedId;
        private String conversationId;

        private EventStream(InputStream body, String requestedId) {
            this.body = body;
            this.requestedId = requestedId;
        }

        @Override
        public void read(Listener listener) throws IOException {
            Reader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
            String name = "";
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = readLine(reader)) != null) {
                if (line.isEmpty()) {
                    if (dispatch(name, data.toString(), listener)) {
                        return;
                    }
                    name = "";
                    data.setLength(0);
                } else if (line.startsWith("event:")) {
                    name = line.substring(6).strip();
                } else if (line.startsWith("data:")) {
                    if (data.length() + line.length() > MAX_FRAME_CHARS) {
                        throw new IOException("coach SSE frame too large");
                    }
                    data.append(line.substring(5)).append('\n');
                }
            }
            throw new IOException("coach SSE ended before done");
        }

        private boolean dispatch(String name, String data, Listener listener) throws IOException {
            switch (name) {
                case "meta" -> {
                    String id = stringField(data, "conversationId");
                    if (conversationId != null
                            || !CoachQuestion.validId(id)
                            || (requestedId != null && !requestedId.equals(id))) {
                        throw new IOException("invalid coach conversation");
                    }
                    conversationId = id;
                    listener.started(id);
                }
                case "delta" -> {
                    requireStarted();
                    listener.delta(stringField(data, "text"));
                }
                case "done" -> {
                    requireStarted();
                    JsonNode payload = events.readTree(data);
                    if (payload == null || !payload.isObject()) {
                        throw new IOException("invalid coach done");
                    }
                    listener.done();
                    return true;
                }
                case "error" -> throw new IOException("coach upstream error");
                default -> {
                    /* tool 사건은 사용자 응답에 포함하지 않는다. */
                }
            }
            return false;
        }

        private void requireStarted() throws IOException {
            if (conversationId == null) {
                throw new IOException("missing coach meta");
            }
        }

        private String stringField(String data, String name) throws IOException {
            JsonNode node = events.readTree(data);
            JsonNode field = node == null ? null : node.get(name);
            if (field == null || !field.isTextual()) {
                throw new IOException("invalid coach SSE data");
            }
            return field.textValue();
        }

        @Override
        public void close() throws IOException {
            body.close();
        }
    }

    private static String readLine(Reader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = reader.read()) != -1 && value != '\n') {
            if (line.length() >= MAX_FRAME_CHARS) {
                throw new IOException("coach SSE line too large");
            }
            line.append((char) value);
        }
        if (value == -1) {
            return null;
        }
        if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
            line.setLength(line.length() - 1);
        }
        return line.toString();
    }
}
