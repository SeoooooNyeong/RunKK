package com.a207.runkk.infra.integration.ai;

import com.a207.runkk.aicoach.CoachChatGateway;
import com.a207.runkk.aicoach.CoachQuestion;
import com.a207.runkk.aicoach.CoachRejected;
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
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AiServerCoachGateway implements CoachChatGateway {
    private static final int MAX_FRAME_CHARS = 64 * 1024;
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
