package com.a207.runkk.infra.integration.ai;

import com.a207.runkk.runbody.RunBodyGateway;
import com.a207.runkk.runbody.RunBodyMonth;
import com.a207.runkk.runbody.RunBodyRejectedException;
import com.a207.runkk.runbody.RunBodyRequest;
import com.a207.runkk.runbody.RunBodySession;
import com.a207.runkk.runbody.RunBodyStatus;
import com.a207.runkk.runbody.RunBodyUnavailableException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * ai 런바디 경로를 부른다(설계 05-19 2.5, D-449·D-450). POST /sessions/{id}/analyze, GET
 * /sessions/{id}/status, GET /sessions/{id}, GET /users/{id}/months/{ym}. 연결은
 * AiServerClient.runbody(읽기 10초, X-Internal-Token)이고 재시도하지 않는다. 결과·월간 본문은 고치지 않고 받은 JSON 그대로
 * 넘긴다(D-442, 앱 계약 1.1·2.1). 요청 본문(심박·위치)과 원격 응답 본문은 로그·예외에 남기지 않는다.
 *
 * <p>D-449의 "요청 gzip"은 하지 않는다: ai(FastAPI)는 요청 본문 압축을 풀지 않아 Content-Encoding: gzip 본문을 422로 거부하고,
 * api → ai는 nginx 없이 직접 닿는다(infra/docker-compose.yml AI_SERVER_URL). 하프 표본도 약 1.5MB로 ai 상한 8MB 안이다.
 */
@Component
public class AiServerRunBodyGateway implements RunBodyGateway {

    /** 결과 본문 상한. 지표 원값(metrics)을 넣어도 수십 KB다. */
    static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    /** ai가 재시도해도 받지 않는다고 정한 제출 실패(ai/tests/contract sessions-analyze-*). */
    private static final Set<Integer> REJECTED = Set.of(409, 413, 422);

    private static final DateTimeFormatter AI_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private static final Map<String, RunBodyStatus> AI_STATUS =
            Map.of(
                    "pending", RunBodyStatus.ANALYZING,
                    "done", RunBodyStatus.DONE,
                    "failed", RunBodyStatus.FAILED);

    private final AiServerClient client;
    private final ObjectMapper json;

    public AiServerRunBodyGateway(AiServerClient client, ObjectMapper json) {
        this.client = client;
        this.json = json;
    }

    @Override
    public void submit(RunBodyRequest request) {
        byte[] body = serialize(SessionIn.of(request));
        Reply reply =
                exchange(
                        client.runbody()
                                .post()
                                .uri("/sessions/{id}/analyze", request.sessionId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(body));
        if (reply.status() == 202) {
            return;
        }
        if (REJECTED.contains(reply.status())) {
            throw new RunBodyRejectedException(reasonOf(reply, "REJECTED_" + reply.status()));
        }
        throw unavailable(reply);
    }

    @Override
    public RunBodyStatus statusOf(String recordId) {
        Reply reply = exchange(client.runbody().get().uri("/sessions/{id}/status", recordId));
        if (reply.status() == 404) {
            return RunBodyStatus.FAILED;
        }
        if (reply.status() != 200) {
            throw unavailable(reply);
        }
        JsonNode body = tree(reply);
        RunBodyStatus status = AI_STATUS.get(body.path("status").asText());
        if (status == null || !recordId.equals(body.path("sessionId").asText())) {
            throw malformed();
        }
        return status;
    }

    @Override
    public RunBodySession sessionOf(String recordId) {
        Reply reply = exchange(client.runbody().get().uri("/sessions/{id}", recordId));
        if (reply.status() == 404) {
            return RunBodySession.notReady(RunBodyStatus.FAILED);
        }
        if (reply.status() == 409) {
            String reason = reasonOf(reply, "");
            if (reason.equals("ANALYSIS_PENDING")) {
                return RunBodySession.notReady(RunBodyStatus.ANALYZING);
            }
            if (reason.equals("ANALYSIS_FAILED")) {
                return RunBodySession.notReady(RunBodyStatus.FAILED);
            }
        }
        if (reply.status() != 200) {
            throw unavailable(reply);
        }
        JsonNode body = tree(reply);
        if (!body.isObject()
                || !recordId.equals(body.path("sessionId").asText())
                || !body.path("userId").isTextual()) {
            throw malformed();
        }
        return RunBodySession.done(body.path("userId").asText(), reply.text());
    }

    @Override
    public RunBodyMonth monthOf(long memberId, YearMonth month) {
        if (memberId <= 0) {
            throw new IllegalArgumentException("회원 ID 오류");
        }
        Reply reply =
                exchange(
                        client.runbody()
                                .get()
                                .uri(
                                        "/users/{id}/months/{ym}",
                                        Long.toString(memberId),
                                        month.toString()));
        if (reply.status() != 200) {
            throw unavailable(reply);
        }
        JsonNode body = tree(reply);
        if (!body.isObject()
                || !month.toString().equals(body.path("yearMonth").asText())
                || !body.path("userId").isTextual()) {
            throw malformed();
        }
        return new RunBodyMonth(body.path("userId").asText(), reply.text());
    }

    private byte[] serialize(SessionIn session) {
        try {
            return json.writeValueAsBytes(session);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("런바디 요청 직렬화 실패");
        }
    }

    private Reply exchange(RestClient.RequestHeadersSpec<?> request) {
        try {
            return request.exchange(
                    (sent, response) -> {
                        byte[] bytes = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                        if (bytes.length > MAX_RESPONSE_BYTES) {
                            throw new RunBodyUnavailableException("RESPONSE_TOO_LARGE", false);
                        }
                        return new Reply(response.getStatusCode().value(), bytes);
                    },
                    true);
        } catch (RestClientException error) {
            boolean connection = connectionFailed(error);
            throw new RunBodyUnavailableException(
                    connection ? "CONNECTION_FAILED" : "TIMEOUT_OR_IO", connection);
        }
    }

    private JsonNode tree(Reply reply) {
        try {
            JsonNode node = json.readTree(reply.bytes());
            if (node == null) {
                throw malformed();
            }
            return node;
        } catch (IOException e) {
            throw malformed();
        }
    }

    private String reasonOf(Reply reply, String fallback) {
        try {
            JsonNode node = json.readTree(reply.bytes());
            String reason = node == null ? "" : node.path("reason").asText("");
            return reason.matches("[A-Z_]{1,40}") ? reason : fallback;
        } catch (IOException e) {
            return fallback;
        }
    }

    private RunBodyUnavailableException unavailable(Reply reply) {
        if (reply.status() == 401) {
            return new RunBodyUnavailableException("UNAUTHORIZED_CHECK_INTERNAL_TOKEN", false);
        }
        return new RunBodyUnavailableException("STATUS_" + reply.status(), false);
    }

    private static RunBodyUnavailableException malformed() {
        return new RunBodyUnavailableException("MALFORMED_RESPONSE", false);
    }

    /** 연결 자체가 안 됐는가(연결 거부·연결 시간 초과·주소 해석 실패). 읽기 시간 초과는 아니다. */
    static boolean connectionFailed(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpConnectTimeoutException
                    || cause instanceof ConnectException
                    || cause instanceof UnresolvedAddressException) {
                return true;
            }
        }
        return false;
    }

    private record Reply(int status, byte[] bytes) {
        String text() {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /** ai SessionIn(extra = forbid). 이름·이메일 같은 회원 식별 항목을 담지 않는다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SessionIn(
            String sessionId,
            String userId,
            String source,
            String startedAt,
            String endedAt,
            long activeMs,
            double distanceMeters,
            Profile profile,
            List<Sample> samples,
            List<Gps> gps,
            List<Recovery> recoveryHeartRates) {

        static SessionIn of(RunBodyRequest r) {
            return new SessionIn(
                    r.sessionId(),
                    r.userId(),
                    r.source(),
                    format(r.startedAt()),
                    r.endedAt() == null ? null : format(r.endedAt()),
                    r.activeMs(),
                    r.distanceMeters(),
                    new Profile(r.birthYear()),
                    r.samples().stream()
                            .map(
                                    s ->
                                            new Sample(
                                                    s.tMs(),
                                                    s.heartRate(),
                                                    s.speed(),
                                                    s.cadence(),
                                                    s.distance()))
                            .toList(),
                    r.gps().stream().map(g -> new Gps(g.tMs(), g.lat(), g.lon(), g.acc())).toList(),
                    r.recoveryHeartRates().stream()
                            .map(h -> new Recovery(h.tMs(), h.heartRate()))
                            .toList());
        }

        private static String format(OffsetDateTime time) {
            return AI_TIME.format(time);
        }
    }

    record Profile(int birthYear) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Sample(
            @JsonProperty("tMs") long tMs,
            Integer heartRate,
            Double speed,
            Double cadence,
            Double distance) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Gps(@JsonProperty("tMs") long tMs, double lat, double lon, Double acc) {}

    record Recovery(@JsonProperty("tMs") long tMs, int heartRate) {}
}
