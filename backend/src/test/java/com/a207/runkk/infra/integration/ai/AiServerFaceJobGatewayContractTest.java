package com.a207.runkk.infra.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.a207.runkk.avatar.FaceJobRejectedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiServerFaceJobGatewayContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final byte[] SELFIE = {1, 2, 3};

    record Fixture(AiServerFaceJobGateway gateway, MockRestServiceServer server) {}

    static Fixture fixture() {
        RestClient.Builder builder =
                RestClient.builder()
                        .baseUrl("http://ai:8000")
                        .defaultHeader("X-Internal-Token", "test-token");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient rest = builder.build();
        return new Fixture(
                new AiServerFaceJobGateway(new AiServerClient(rest, rest), JSON), server);
    }

    static Stream<Path> samples() throws IOException {
        try (Stream<Path> paths = Files.list(Path.of("../ai/tests/contract"))) {
            List<Path> samples =
                    paths.filter(p -> p.getFileName().toString().startsWith("faces-"))
                            .filter(p -> p.toString().endsWith(".json"))
                            .sorted()
                            .toList();
            assertThat(samples).hasSize(19);
            return samples.stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void replaysCurrentAiContract(Path path) throws IOException {
        JsonNode sample = JSON.readTree(Files.readString(path));
        String endpoint = sample.path("endpoint").asText();
        String jobId = sample.path("request").path("jobId").asText();
        String url =
                endpoint.substring(endpoint.indexOf(' ') + 1)
                        .replace("{userId}", "42")
                        .replace("{jobId}", jobId);
        int status = sample.path("status").asInt();
        Fixture f = fixture();
        var request =
                f.server
                        .expect(requestTo("http://ai:8000" + url))
                        .andExpect(header("X-Internal-Token", "test-token"))
                        .andExpect(
                                method(
                                        endpoint.startsWith("POST")
                                                ? HttpMethod.POST
                                                : HttpMethod.GET));
        if (endpoint.startsWith("POST")) {
            request.andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                    .andExpect(content().bytes(SELFIE));
        }
        request.andRespond(
                withStatus(HttpStatusCode.valueOf(status))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(sample.path("body").toString()));
        if (status < 300) {
            Object actual = invoke(f.gateway, endpoint, jobId);
            JsonNode result = JSON.valueToTree(actual);
            assertThat(result).isEqualTo(sample.path("body"));
        } else {
            assertThatThrownBy(() -> invoke(f.gateway, endpoint, jobId))
                    .isInstanceOfSatisfying(
                            FaceJobRejectedException.class,
                            error -> {
                                assertThat(error.status()).isEqualTo(status == 401 ? 503 : status);
                                assertThat(error.reason())
                                        .isEqualTo(
                                                status == 401
                                                        ? "UPSTREAM_FAILED"
                                                        : sample.path("body")
                                                                .path("reason")
                                                                .asText());
                            });
        }
        f.server.verify();
    }

    private static Object invoke(AiServerFaceJobGateway gateway, String endpoint, String jobId) {
        if (endpoint.startsWith("POST")) return gateway.submit(42, SELFIE, 1);
        if (endpoint.endsWith("/result")) return gateway.result(42, jobId);
        if (endpoint.endsWith("/latest")) return gateway.latest(42);
        return gateway.status(42, jobId);
    }

    @Test
    void explicitRetryAttemptIsSentAsQuery() {
        // DV-성-9: 명시적 재시도는 ai가 새 멱등 키를 쓰도록 attempt를 붙인다(1이면 붙이지 않는다)
        Fixture f = fixture();
        f.server
                .expect(requestTo("http://ai:8000/users/42/faces/jobs?attempt=2"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().bytes(SELFIE))
                .andRespond(
                        withStatus(HttpStatusCode.valueOf(202))
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"jobId\":\"job-2\",\"status\":\"QUEUED\"}"));
        assertThat(f.gateway.submit(42, SELFIE, 2).jobId()).isEqualTo("job-2");
        f.server.verify();
    }

    @Test
    void timeoutIsNotReportedAsMissingFace() {
        Fixture f = fixture();
        f.server
                .expect(requestTo("http://ai:8000/users/42/faces/latest"))
                .andRespond(withException(new HttpTimeoutException("timeout")));
        assertThatThrownBy(() -> f.gateway.latest(42))
                .isInstanceOfSatisfying(
                        FaceJobRejectedException.class,
                        e -> {
                            assertThat(e.status()).isEqualTo(504);
                            assertThat(e.reason()).isEqualTo("TIMEOUT");
                        });
    }

    @Test
    void malformedJobResponseIsRejected() {
        Fixture f = fixture();
        f.server
                .expect(requestTo("http://ai:8000/users/42/faces/jobs/a"))
                .andRespond(
                        withSuccess(
                                "{\"jobId\":\"a\",\"status\":\"UNKNOWN\"}",
                                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> f.gateway.status(42, "a"))
                .isInstanceOf(FaceJobRejectedException.class);
    }

    @Test
    void oversizedDecodedImageIsRejected() throws IOException {
        JsonNode sample =
                JSON.readTree(
                        Files.readString(Path.of("../ai/tests/contract/faces-latest-200-ok.json")));
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) sample.path("body");
        byte[] png =
                java.util.Base64.getDecoder()
                        .decode(body.path("faceImage").asText().split(",", 2)[1]);
        java.nio.ByteBuffer.wrap(png).putInt(16, 100_000);
        body.put(
                "faceImage",
                "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(png));
        Fixture f = fixture();
        f.server
                .expect(requestTo("http://ai:8000/users/42/faces/latest"))
                .andRespond(withSuccess(body.toString(), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> f.gateway.latest(42)).isInstanceOf(FaceJobRejectedException.class);
    }

    @Test
    void overlongErrorCannotReachPersistence() {
        Fixture f = fixture();
        String response =
                "{\"jobId\":\"a\",\"status\":\"FAILED\",\"error\":{\"code\":\"FAIL\",\"message\":\""
                        + "x".repeat(501)
                        + "\"}}";
        f.server
                .expect(requestTo("http://ai:8000/users/42/faces/jobs/a"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> f.gateway.status(42, "a"))
                .isInstanceOf(FaceJobRejectedException.class);
    }

    @Test
    void additiveUpstreamFieldsRemainCompatible() {
        Fixture f = fixture();
        f.server
                .expect(requestTo("http://ai:8000/users/42/faces/jobs/a"))
                .andRespond(
                        withSuccess(
                                "{\"jobId\":\"a\",\"status\":\"RUNNING\",\"newMetadata\":42}",
                                MediaType.APPLICATION_JSON));
        assertThat(f.gateway.status(42, "a").status())
                .isEqualTo(com.a207.runkk.avatar.FaceJob.Status.RUNNING);
    }

    @Test
    void pathTraversalIsRejectedBeforeCallingAi() {
        Fixture f = fixture();
        assertThatThrownBy(() -> f.gateway.status(42, "../other"))
                .isInstanceOf(IllegalArgumentException.class);
        f.server.verify();
    }

    /** W9 렌더러 입력(S15P31A207-299, contract-W8.md 6.2): 옆모습 레이어·원본 5장과 rightLayerLayout을 앱까지 넘긴다. */
    private static com.fasterxml.jackson.databind.node.ObjectNode eightLayerBody()
            throws IOException {
        JsonNode sample =
                JSON.readTree(
                        Files.readString(Path.of("../ai/tests/contract/faces-result-200-ok.json")));
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) sample.path("body").deepCopy();
        String png = body.path("faceImage").asText();
        for (String field :
                List.of(
                        "frontImage",
                        "rightImage",
                        "rightFaceImage",
                        "rightEyesImage",
                        "rightHairImage")) {
            body.put(field, png);
        }
        var layout = body.putObject("rightLayerLayout");
        layout.put("version", "runkk-right-head-layers-v1");
        layout.put("direction", "right");
        layout.putArray("canvas").add(1024).add(1024);
        var mobile = (com.fasterxml.jackson.databind.node.ObjectNode) body.path("mobile");
        mobile.putObject("neckMask").put("top", 0.8);
        return body;
    }

    @Test
    void eightLayersAndNeckMaskReachTheApp() throws IOException {
        var body = eightLayerBody();
        Fixture f = fixture();
        f.server
                .expect(requestTo("http://ai:8000/users/42/faces/jobs/job-8/result"))
                .andRespond(withSuccess(body.toString(), MediaType.APPLICATION_JSON));

        com.a207.runkk.avatar.GeneratedHead head = f.gateway.result(42, "job-8");

        assertThat(head.rightFaceImage()).isEqualTo(body.path("faceImage").asText());
        assertThat(head.frontImage()).isNotNull();
        assertThat(head.rightImage()).isNotNull();
        assertThat(head.rightEyesImage()).isNotNull();
        assertThat(head.rightHairImage()).isNotNull();
        assertThat(head.rightLayerLayout()).containsEntry("direction", "right");
        assertThat(head.mobile()).containsKey("neckMask");
        JsonNode tree = JSON.valueToTree(head);
        assertThat(tree).isEqualTo(body);
    }

    @Test
    void missingSideLayersAreOmittedNotNull() throws IOException {
        JsonNode sample =
                JSON.readTree(
                        Files.readString(Path.of("../ai/tests/contract/faces-result-200-ok.json")));
        Fixture f = fixture();
        f.server
                .expect(requestTo("http://ai:8000/users/42/faces/jobs/job-3/result"))
                .andRespond(
                        withSuccess(sample.path("body").toString(), MediaType.APPLICATION_JSON));

        JsonNode out = JSON.valueToTree(f.gateway.result(42, "job-3"));

        assertThat(out.has("rightFaceImage")).isFalse();
        assertThat(out.has("rightLayerLayout")).isFalse();
    }

    @Test
    void invalidSideLayerIsRejected() throws IOException {
        var body = eightLayerBody();
        body.put("rightHairImage", "data:image/png;base64,AAAA");
        Fixture f = fixture();
        f.server
                .expect(requestTo("http://ai:8000/users/42/faces/jobs/job-9/result"))
                .andRespond(withSuccess(body.toString(), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> f.gateway.result(42, "job-9"))
                .isInstanceOf(FaceJobRejectedException.class);
    }
}
