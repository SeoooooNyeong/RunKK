package com.a207.runkk.infra.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.a207.runkk.analysis.Comment;
import com.a207.runkk.analysis.CommentSource;
import com.a207.runkk.analysis.FitnessSummary;
import com.a207.runkk.running.RunningScore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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
 * ai 서버 /comments 계약 시험(14장 "단위(ai 서버)"의 api 쪽, D-58, NFR-024). ai 저장소의
 * 표본(contract/ai/comment-*.json)을 그대로 재생하고 표본의 gateway 항목(200이면 Optional.of(text), 그 밖은
 * Optional.empty())이 나오는지 본다. 요청은 SummaryIn 6항목뿐이라 회원 식별자(422 표본의 memberId)·기록 순번(fromRun·toRun)이
 * 없다.
 */
@DisplayName("AiServerCommentGateway ↔ ai 서버 /comments 표본")
class AiServerCommentGatewayContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TOKEN = "test-internal-token";
    private static final String URL = "http://ai:8000/comments";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    /** 표본 요청과 같은 값(평균 42.5, 거리 +1200m, 페이스 −8초, 토요일, 시간대 2, 10회). */
    private static final FitnessSummary SUMMARY =
            new FitnessSummary(
                    11, 20, new RunningScore(42.5), 1_200, -8, DayOfWeek.SATURDAY, 2, 0.0);

    record Fixture(AiServerCommentGateway gateway, MockRestServiceServer server) {}

    static Fixture fixture() {
        RestClient.Builder builder =
                RestClient.builder()
                        .baseUrl("http://ai:8000")
                        .defaultHeader("X-Internal-Token", TOKEN);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiServerClient client = new AiServerClient(builder.build(), builder.build());
        return new Fixture(new AiServerCommentGateway(client, JSON), server);
    }

    static Stream<String> samples() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("src/test/resources/contract/ai"))) {
            List<String> names =
                    files.map(p -> p.getFileName().toString())
                            .filter(n -> n.startsWith("comment-") && n.endsWith(".json"))
                            .sorted()
                            .toList();
            assertThat(names).as("ai 표본 comment-*.json").hasSize(5);
            return names.stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    @DisplayName("표본마다 SummaryIn 6항목만 보내고 gateway 항목의 결과로 바꾼다")
    void sample_isTranslatedAsDocumented(String name) throws IOException {
        JsonNode sample = JSON.readTree(read(name));
        ObjectNode expectedRequest = sample.path("request").path("json").deepCopy();
        expectedRequest.remove("memberId");
        Fixture f = fixture();
        f.server()
                .expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", TOKEN))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(JSON.writeValueAsString(expectedRequest), true))
                .andRespond(
                        withStatus(HttpStatus.valueOf(sample.get("status").asInt()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(JSON.writeValueAsString(sample.get("body"))));

        Optional<Comment> comment = f.gateway().requestComment(SUMMARY, TIMEOUT);

        f.server().verify();
        if (sample.get("gateway").asText().startsWith("Optional.of")) {
            assertThat(comment)
                    .as(name)
                    .get()
                    .extracting(Comment::text, Comment::source, Comment::forRun)
                    .containsExactly(
                            sample.path("body").path("text").asText(), CommentSource.AI, 20);
        } else {
            assertThat(sample.get("gateway").asText()).as(name).startsWith("Optional.empty()");
            assertThat(comment).as(name).isEmpty();
        }
    }

    @Test
    @DisplayName("빈 문구·120자 초과·JSON 아님은 Optional.empty()")
    void invalidSuccessBody_isEmpty() {
        for (String body :
                List.of("{\"text\":\"  \"}", "{\"text\":\"" + "가".repeat(121) + "\"}", "oops")) {
            Fixture f = fixture();
            f.server()
                    .expect(requestTo(URL))
                    .andRespond(
                            withStatus(HttpStatus.OK)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .body(body));
            assertThat(f.gateway().requestComment(SUMMARY, TIMEOUT)).as(body).isEmpty();
        }
    }

    @Test
    @DisplayName("읽기 시간 초과(3초)·연결 실패는 Optional.empty()(규칙 문구로 대체, 재시도 없음)")
    void timeoutAndConnectionFailure_areEmpty() {
        Fixture slow = fixture();
        slow.server()
                .expect(requestTo(URL))
                .andRespond(withException(new HttpTimeoutException("request timed out")));
        assertThat(slow.gateway().requestComment(SUMMARY, TIMEOUT)).isEmpty();
        slow.server().verify();

        Fixture down = fixture();
        down.server()
                .expect(requestTo(URL))
                .andRespond(withException(new ConnectException("refused")));
        assertThat(down.gateway().requestComment(SUMMARY, TIMEOUT)).isEmpty();
        down.server().verify();
    }

    private static String read(String name) throws IOException {
        try (InputStream in =
                AiServerCommentGatewayContractTest.class.getResourceAsStream(
                        "/contract/ai/" + name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
