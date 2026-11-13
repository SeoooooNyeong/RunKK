package com.a207.runkk.infra.integration.ai;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.a207.runkk.aiprivacy.AiUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiServerPrivacyGatewayContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    record Fixture(AiServerPrivacyGateway gateway, MockRestServiceServer server) {}

    static Fixture fixture() {
        RestClient.Builder builder =
                RestClient.builder()
                        .baseUrl("http://ai:8000")
                        .defaultHeader("X-Internal-Token", "test-token");
        var server = MockRestServiceServer.bindTo(builder).build();
        RestClient rest = builder.build();
        return new Fixture(
                new AiServerPrivacyGateway(new AiServerClient(rest, rest), JSON), server);
    }

    static Stream<Path> samples() throws IOException {
        try (var paths = Files.list(Path.of("../ai/tests/contract"))) {
            List<Path> samples =
                    paths.filter(p -> p.getFileName().toString().startsWith("users-delete-"))
                            .filter(p -> p.toString().endsWith(".json"))
                            .sorted()
                            .toList();
            assertThat(samples).hasSize(4);
            return samples.stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void replaysDeleteContract(Path samplePath) throws Exception {
        var sample = JSON.readTree(Files.readString(samplePath));
        Fixture f = fixture();
        f.server
                .expect(requestTo("http://ai:8000/users/42"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Internal-Token", "test-token"))
                .andExpect(content().bytes(new byte[0]))
                .andRespond(
                        withStatus(HttpStatusCode.valueOf(sample.path("status").asInt()))
                                .body(sample.path("body").toString())
                                .contentType(MediaType.APPLICATION_JSON));
        if (sample.path("status").asInt() == 200) {
            assertThatCode(() -> f.gateway.erase(42)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> f.gateway.erase(42))
                    .isInstanceOf(AiUnavailableException.class);
        }
        f.server.verify();
    }

    @Test
    void optionalAvatarCountAndRepeatedZeroResultsAreAccepted() {
        Fixture f = fixture();
        for (int count : new int[] {1, 0}) {
            f.server
                    .expect(requestTo("http://ai:8000/users/42"))
                    .andRespond(
                            withSuccess(
                                    "{\"sessionResults\":0,\"coachMessages\":0,\"avatarImages\":"
                                            + count
                                            + "}",
                                    MediaType.APPLICATION_JSON));
        }
        f.gateway.erase(42);
        f.gateway.erase(42);
        f.server.verify();
    }

    @Test
    void timeoutAndMalformedSuccessRemainRetryable() {
        Fixture f = fixture();
        f.server
                .expect(requestTo("http://ai:8000/users/42"))
                .andRespond(withException(new HttpTimeoutException("do-not-log-request")));
        f.server
                .expect(requestTo("http://ai:8000/users/42"))
                .andRespond(
                        withSuccess(
                                "{\"sessionResults\":-1,\"coachMessages\":0}",
                                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> f.gateway.erase(42)).isInstanceOf(AiUnavailableException.class);
        assertThatThrownBy(() -> f.gateway.erase(42)).isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void explicitStorageTimeoutRemainsRetryable() {
        Fixture f = fixture();
        f.server
                .expect(requestTo("http://ai:8000/users/42"))
                .andRespond(
                        withStatus(HttpStatus.GATEWAY_TIMEOUT)
                                .body("{\"reason\":\"STORAGE_TIMEOUT\"}")
                                .contentType(MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> f.gateway.erase(42)).isInstanceOf(AiUnavailableException.class);
    }
}
