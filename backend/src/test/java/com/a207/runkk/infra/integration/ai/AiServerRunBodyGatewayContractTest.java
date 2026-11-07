package com.a207.runkk.infra.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.a207.runkk.runbody.RunBodyMonth;
import com.a207.runkk.runbody.RunBodyRejectedException;
import com.a207.runkk.runbody.RunBodyRequest;
import com.a207.runkk.runbody.RunBodySession;
import com.a207.runkk.runbody.RunBodyStatus;
import com.a207.runkk.runbody.RunBodyUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * ai 런바디 계약 표본(ai/tests/contract sessions-*·months-*)을 그대로 재생해 게이트웨이 해석을 확인한다(05-19 D-449·D-450, 7절
 * 계약 시험의 서버 쪽 절반).
 */
@DisplayName("ai 런바디 게이트웨이 계약")
class AiServerRunBodyGatewayContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path CONTRACT = Path.of("../ai/tests/contract");

    record Fixture(AiServerRunBodyGateway gateway, MockRestServiceServer server) {}

    static Fixture fixture() {
        RestClient.Builder builder =
                RestClient.builder()
                        .baseUrl("http://ai:8000")
                        .defaultHeader("X-Internal-Token", "test-token");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient rest = builder.build();
        return new Fixture(
                new AiServerRunBodyGateway(new AiServerClient(rest, rest), JSON), server);
    }

    static RunBodyRequest request(String sessionId) {
        OffsetDateTime start = OffsetDateTime.of(2026, 10, 19, 19, 9, 39, 0, ZoneOffset.ofHours(9));
        return new RunBodyRequest(
                sessionId,
                "42",
                "watch",
                start,
                start.plusMinutes(18),
                1_084_930,
                3008,
                2000,
                List.of(
                        new RunBodyRequest.Sample(0, 86, null, null),
                        new RunBodyRequest.Sample(1024, null, 2.374, 2.374)),
                List.of(new RunBodyRequest.GpsPoint(1024, 37.5, 127.0, 5.0)),
                List.of(new RunBodyRequest.RecoveryHeartRate(60_000, 120)));
    }

    static Stream<Path> samples() throws IOException {
        try (Stream<Path> paths = Files.list(CONTRACT)) {
            List<Path> samples =
                    paths.filter(
                                    p ->
                                            p.getFileName().toString().startsWith("sessions-")
                                                    || p.getFileName()
                                                            .toString()
                                                            .startsWith("months-"))
                            .filter(p -> p.toString().endsWith(".json"))
                            .sorted()
                            .toList();
            assertThat(samples).hasSize(15);
            return samples.stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void replaysAiContract(Path path) throws IOException {
        JsonNode sample = JSON.readTree(Files.readString(path));
        String endpoint = sample.path("endpoint").asText();
        int status = sample.path("status").asInt();
        String reason = sample.path("body").path("reason").asText("");
        JsonNode request = sample.path("request");
        Fixture f = fixture();
        String sessionId = request.path("sessionId").asText();
        String url =
                endpoint.substring(endpoint.indexOf(' ') + 1)
                        .replace("{sessionId}", sessionId)
                        .replace("{userId}", "42")
                        .replace("{yearMonth}", request.path("yearMonth").asText());
        JsonNode body = sample.path("body");
        if (endpoint.startsWith("GET /users") && status == 422) {
            // 서버는 월 형식을 RunBodyController에서 먼저 거른다(400 INVALID_YEAR_MONTH). ai에 보내지 않는다
            assertThatThrownBy(() -> YearMonth.parse(request.path("yearMonth").asText()))
                    .isInstanceOf(java.time.format.DateTimeParseException.class);
            return;
        }
        f.server
                .expect(requestTo("http://ai:8000" + url))
                .andExpect(header("X-Internal-Token", "test-token"))
                .andExpect(method(endpoint.startsWith("POST") ? HttpMethod.POST : HttpMethod.GET))
                .andRespond(
                        withStatus(HttpStatusCode.valueOf(status))
                                .body(body.toString())
                                .contentType(MediaType.APPLICATION_JSON));

        if (endpoint.startsWith("POST")) {
            if (status == 202) {
                f.gateway.submit(request(sessionId));
            } else if (status == 401) {
                assertThatThrownBy(() -> f.gateway.submit(request(sessionId)))
                        .isInstanceOf(RunBodyUnavailableException.class)
                        .extracting(e -> ((RunBodyUnavailableException) e).connectionFailed())
                        .isEqualTo(false);
            } else {
                assertThatThrownBy(() -> f.gateway.submit(request(sessionId)))
                        .as("409·413·422는 재시도하지 않는다")
                        .isInstanceOf(RunBodyRejectedException.class)
                        .hasMessage(reason);
            }
        } else if (endpoint.endsWith("/status")) {
            if (status == 200) {
                assertThat(f.gateway.statusOf(sessionId)).isEqualTo(RunBodyStatus.DONE);
            } else if (status == 404) {
                assertThat(f.gateway.statusOf(sessionId))
                        .as("ai가 세션을 모르면 다시 제출한다")
                        .isEqualTo(RunBodyStatus.FAILED);
            } else {
                assertThatThrownBy(() -> f.gateway.statusOf(sessionId))
                        .isInstanceOf(RunBodyUnavailableException.class);
            }
        } else if (endpoint.startsWith("GET /sessions")) {
            if (status == 200) {
                RunBodySession session = f.gateway.sessionOf(sessionId);
                assertThat(session.status()).isEqualTo(RunBodyStatus.DONE);
                assertThat(session.userId()).isEqualTo("u_demo");
                assertThat(JSON.readTree(session.body()))
                        .as("ai 본문을 그대로 넘긴다(D-442)")
                        .isEqualTo(sample.path("body"));
            } else if (status == 409) {
                assertThat(f.gateway.sessionOf(sessionId).status())
                        .isEqualTo(RunBodyStatus.ANALYZING);
            } else if (status == 404) {
                assertThat(f.gateway.sessionOf(sessionId).status()).isEqualTo(RunBodyStatus.FAILED);
            } else {
                assertThatThrownBy(() -> f.gateway.sessionOf(sessionId))
                        .isInstanceOf(RunBodyUnavailableException.class);
            }
        } else {
            YearMonth month = YearMonth.parse(request.path("yearMonth").asText());
            if (status == 200) {
                RunBodyMonth found = f.gateway.monthOf(42, month);
                assertThat(found.userId()).isEqualTo("u_demo");
                assertThat(JSON.readTree(found.body())).isEqualTo(body);
            } else {
                assertThatThrownBy(() -> f.gateway.monthOf(42, month))
                        .isInstanceOf(RunBodyUnavailableException.class);
            }
        }
    }

    @Test
    void submit_sendsSessionInFieldsOnly_matchingAiFixtureKeys() throws IOException {
        Fixture f = fixture();
        List<String> sent = new ArrayList<>();
        f.server
                .expect(requestTo("http://ai:8000/sessions/run-1/analyze"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(r -> sent.add(((MockClientHttpRequest) r).getBodyAsString()))
                .andRespond(
                        withStatus(HttpStatusCode.valueOf(202))
                                .body("{\"sessionId\":\"run-1\",\"status\":\"pending\"}")
                                .contentType(MediaType.APPLICATION_JSON));

        f.gateway.submit(request("run-1"));

        JsonNode body = JSON.readTree(sent.get(0));
        JsonNode fixture =
                JSON.readTree(Files.readString(Path.of("../ai/tests/fixtures/second_run.json")));
        // ai schemas.py SessionIn(extra = forbid)의 항목. ai 시험 고정물도 같은 항목 안에 있다
        Set<String> sessionIn =
                Set.of(
                        "sessionId",
                        "userId",
                        "source",
                        "startedAt",
                        "endedAt",
                        "activeMs",
                        "distanceMeters",
                        "profile",
                        "samples",
                        "gps",
                        "recoveryHeartRates");
        assertThat(fieldNames(fixture)).isSubsetOf(sessionIn);
        assertThat(fieldNames(body)).isEqualTo(new TreeSet<>(sessionIn));
        assertThat(body.path("startedAt").asText()).isEqualTo("2026-10-19T19:09:39+09:00");
        assertThat(body.path("profile").path("birthYear").asInt()).isEqualTo(2000);
        assertThat(body.path("samples").get(0).toString())
                .isEqualTo("{\"tMs\":0,\"heartRate\":86}");
        assertThat(body.path("samples").get(1).toString())
                .isEqualTo("{\"tMs\":1024,\"speed\":2.374,\"distance\":2.374}");
        assertThat(body.path("gps").get(0).toString())
                .isEqualTo("{\"tMs\":1024,\"lat\":37.5,\"lon\":127.0,\"acc\":5.0}");
        assertThat(body.path("recoveryHeartRates").get(0).toString())
                .isEqualTo("{\"tMs\":60000,\"heartRate\":120}");
        assertThat(sent.get(0)).doesNotContain("nickname").doesNotContain("email");
        f.server.verify();
    }

    @Test
    void transportFailures_distinguishConnectionFromTimeout() {
        Fixture f = fixture();
        f.server
                .expect(requestTo("http://ai:8000/sessions/run-1/status"))
                .andRespond(withException(new ConnectException("refused")));
        f.server
                .expect(requestTo("http://ai:8000/sessions/run-1/status"))
                .andRespond(withException(new HttpConnectTimeoutException("connect")));
        f.server
                .expect(requestTo("http://ai:8000/sessions/run-1/status"))
                .andRespond(withException(new HttpTimeoutException("read")));
        f.server
                .expect(requestTo("http://ai:8000/sessions/run-1/status"))
                .andRespond(
                        withStatus(HttpStatusCode.valueOf(503))
                                .body("{\"reason\":\"ENGINE_BUSY\"}")
                                .contentType(MediaType.APPLICATION_JSON));

        assertThat(connectionFailed(f, "run-1")).isTrue();
        assertThat(connectionFailed(f, "run-1")).isTrue();
        assertThat(connectionFailed(f, "run-1")).isFalse();
        assertThat(connectionFailed(f, "run-1")).isFalse();
    }

    @Test
    void sessionResult_forAnotherSessionOrGarbage_isUnavailable() {
        Fixture f = fixture();
        f.server
                .expect(requestTo("http://ai:8000/sessions/run-1"))
                .andRespond(
                        withStatus(HttpStatusCode.valueOf(200))
                                .body("{\"sessionId\":\"run-2\",\"userId\":\"42\"}")
                                .contentType(MediaType.APPLICATION_JSON));
        f.server
                .expect(requestTo("http://ai:8000/sessions/run-1"))
                .andRespond(
                        withStatus(HttpStatusCode.valueOf(200))
                                .body("not json")
                                .contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> f.gateway.sessionOf("run-1"))
                .isInstanceOf(RunBodyUnavailableException.class);
        assertThatThrownBy(() -> f.gateway.sessionOf("run-1"))
                .isInstanceOf(RunBodyUnavailableException.class);
    }

    @Test
    void analysisFailedResult_isFailed() {
        Fixture f = fixture();
        f.server
                .expect(requestTo("http://ai:8000/sessions/run-1"))
                .andRespond(
                        withStatus(HttpStatusCode.valueOf(409))
                                .body("{\"reason\":\"ANALYSIS_FAILED\"}")
                                .contentType(MediaType.APPLICATION_JSON));
        assertThat(f.gateway.sessionOf("run-1").status()).isEqualTo(RunBodyStatus.FAILED);
    }

    private static boolean connectionFailed(Fixture f, String id) {
        try {
            f.gateway.statusOf(id);
            throw new AssertionError("예외가 나야 한다");
        } catch (RunBodyUnavailableException e) {
            return e.connectionFailed();
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
