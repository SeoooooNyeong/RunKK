package com.a207.runkk.infra.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.a207.runkk.avatar.FacePart;
import com.a207.runkk.avatar.FaceTransformOutcome;
import com.a207.runkk.avatar.FailureReason;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * ai 서버 /faces 계약 시험(14장 "단위(ai 서버)"의 api 쪽, D-58·D-59). ai 저장소의 응답 표본(contract/ai/face-*.json,
 * 성공·실패 reason 6종·413·415·401)을 MockRestServiceServer로 그대로 재생하고, 표본의 gateway 항목이 적은
 * 결과(FaceTransformOutcome 성공 또는 FailureReason)가 나오는지 본다. 요청은 octet-stream 본문 + X-Internal-Token이고
 * 회원 식별자가 없다.
 */
@DisplayName("AiServerFaceGateway ↔ ai 서버 /faces 표본")
class AiServerFaceGatewayContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TOKEN = "test-internal-token";
    private static final Pattern REASON = Pattern.compile("FailureReason\\.([A-Z_]+)");
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

    record Fixture(AiServerFaceGateway gateway, MockRestServiceServer server) {}

    static Fixture fixture() {
        RestClient.Builder builder =
                RestClient.builder()
                        .baseUrl("http://ai:8000")
                        .defaultHeader("X-Internal-Token", TOKEN);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiServerClient client = new AiServerClient(builder.build(), builder.build());
        return new Fixture(new AiServerFaceGateway(client, JSON), server);
    }

    static Stream<String> samples() throws IOException {
        Path dir = Path.of("src/test/resources/contract/ai");
        try (Stream<Path> files = Files.list(dir)) {
            List<String> names =
                    files.map(p -> p.getFileName().toString())
                            .filter(n -> n.startsWith("face-") && n.endsWith(".json"))
                            .sorted()
                            .toList();
            assertThat(names).as("ai 표본 face-*.json").hasSizeGreaterThanOrEqualTo(10);
            return names.stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    @DisplayName("표본마다 gateway 항목의 결과로 바꾼다")
    void sample_isTranslatedAsDocumented(String name) throws IOException {
        JsonNode sample = JSON.readTree(read(name));
        Fixture f = fixture();
        f.server()
                .expect(requestTo("http://ai:8000/faces"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(JPEG))
                .andRespond(
                        withStatus(HttpStatus.valueOf(sample.get("status").asInt()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(JSON.writeValueAsString(sample.get("body"))));

        FaceTransformOutcome outcome = f.gateway().transform(JPEG);

        f.server().verify();
        String expected = sample.get("gateway").asText();
        Matcher reason = REASON.matcher(expected);
        if (reason.find()) {
            assertThat(outcome.isSuccess()).as(name).isFalse();
            assertThat(outcome.failure())
                    .as(name)
                    .isEqualTo(FailureReason.valueOf(reason.group(1)));
        } else {
            assertThat(expected).startsWith("FaceTransformOutcome 성공");
            assertThat(outcome.isSuccess()).as(name).isTrue();
            assertThat(outcome.images().images()).containsOnlyKeys(FacePart.values());
            assertThat(outcome.images().images().get(FacePart.EYES))
                    .startsWith(0x89, 'P', 'N', 'G');
        }
    }

    @Test
    @DisplayName("200이 아닌 응답에 reason이 없거나 모르는 값이면 UPSTREAM_FAILED(설계 공백 U15)")
    void unknownOrMissingReason_isUpstreamFailed() {
        for (String body : List.of("{\"detail\":\"bad\"}", "{\"reason\":\"WHAT\"}", "not json")) {
            Fixture f = fixture();
            f.server()
                    .expect(requestTo("http://ai:8000/faces"))
                    .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body(body));
            assertThat(f.gateway().transform(JPEG).failure())
                    .as(body)
                    .isEqualTo(FailureReason.UPSTREAM_FAILED);
        }
    }

    @Test
    @DisplayName("200이어도 부위가 모자라거나 base64가 깨졌으면 UPSTREAM_FAILED")
    void malformedSuccess_isUpstreamFailed() {
        for (String body :
                List.of(
                        "{\"parts\":{\"EYES\":\"iVBORw0KGgo=\"}}",
                        "{\"parts\":{\"EYES\":\"%%\",\"NOSE\":\"%%\",\"MOUTH\":\"%%\",\"EARS\":\"%%\","
                            + "\"HAIR\":\"%%\"}}")) {
            Fixture f = fixture();
            f.server()
                    .expect(requestTo("http://ai:8000/faces"))
                    .andRespond(
                            withStatus(HttpStatus.OK)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(body));
            assertThat(f.gateway().transform(JPEG).failure())
                    .isEqualTo(FailureReason.UPSTREAM_FAILED);
        }
    }

    @Test
    @DisplayName("읽기 시간 초과는 TIMEOUT, 연결 실패는 UPSTREAM_FAILED(재시도 없음)")
    void timeoutsAndConnectionFailures() {
        Fixture slow = fixture();
        slow.server()
                .expect(requestTo("http://ai:8000/faces"))
                .andRespond(withException(new HttpTimeoutException("request timed out")));
        assertThat(slow.gateway().transform(JPEG).failure()).isEqualTo(FailureReason.TIMEOUT);
        slow.server().verify();

        Fixture down = fixture();
        down.server()
                .expect(requestTo("http://ai:8000/faces"))
                .andRespond(withException(new ConnectException("refused")));
        assertThat(down.gateway().transform(JPEG).failure())
                .isEqualTo(FailureReason.UPSTREAM_FAILED);
        down.server().verify();
    }

    @Test
    @DisplayName("코멘트 읽기 시간 제한이 ai 라우터 제한 2.7초 이하면 기동을 멈춘다(D-58, H-22)")
    void commentTimeout_mustExceedAiRouterLimit() {
        assertThatThrownBy(
                        () ->
                                new AiServerClient(
                                        "http://ai:8000",
                                        TOKEN,
                                        Duration.ofMillis(2_700),
                                        Duration.ofSeconds(15)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(
                        new AiServerClient(
                                "http://ai:8000",
                                TOKEN,
                                Duration.ofSeconds(3),
                                Duration.ofSeconds(15)))
                .isNotNull();
    }

    private static String read(String name) throws IOException {
        try (InputStream in =
                AiServerFaceGatewayContractTest.class.getResourceAsStream("/contract/ai/" + name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @org.junit.jupiter.api.Test
    void settings_toStringHidesInternalToken() {
        org.assertj.core.api.Assertions.assertThat(
                        new AiServerClient.Server(
                                        "http://ai:8000",
                                        "secret-token",
                                        Duration.ofSeconds(3),
                                        Duration.ofSeconds(15))
                                .toString())
                .doesNotContain("secret-token");
    }
}
