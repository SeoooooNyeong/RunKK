package com.a207.runkk.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * 응답 압축(2026-11-01 사용자 결정 W24, 코드 리뷰 12차 BE12-1): 워치가 미리 받는 셀럽 상대 기록(1초 1점)처럼 큰 JSON 응답은
 * Accept-Encoding: gzip이면 gzip으로 보낸다. 내장 Tomcat이 압축하므로 실제 포트로 시험한다. 문맥은 ManagementEndpointTest와 같은
 * 설정이라 함께 쓴다.
 */
@AutoConfigureObservability(tracing = false)
@SpringBootTest(
        classes = RunkkApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@ActiveProfiles("test")
@DisplayName("JSON 응답 gzip 압축(W24, BE12-1)")
class ResponseCompressionTest {

    /** 공개 경로 중 2KB를 넘는 JSON 응답(springdoc OpenAPI 문서). */
    private static final String LARGE_JSON = "/v3/api-docs";

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort int serverPort;

    @Test
    @DisplayName("Accept-Encoding: gzip이면 큰 JSON 응답에 Content-Encoding: gzip을 붙이고, 풀면 원래 JSON이다")
    void largeJson_isGzipped_whenAccepted() throws Exception {
        HttpResponse<byte[]> plain = get(LARGE_JSON, null);
        HttpResponse<byte[]> gzipped = get(LARGE_JSON, "gzip");

        assertThat(plain.statusCode()).isEqualTo(200);
        assertThat(plain.body().length).isGreaterThan(2048);
        assertThat(plain.headers().firstValue("Content-Encoding")).isEmpty();
        assertThat(gzipped.statusCode()).isEqualTo(200);
        assertThat(gzipped.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        assertThat(gzipped.headers().firstValue("Content-Encoding")).hasValue("gzip");
        assertThat(gzipped.body().length).isLessThan(plain.body().length);
        assertThat(gunzip(gzipped.body()))
                .isEqualTo(new String(plain.body(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Accept-Encoding이 없으면 압축하지 않는다")
    void noAcceptEncoding_isPlain() throws Exception {
        assertThat(get(LARGE_JSON, null).headers().firstValue("Content-Encoding")).isEmpty();
    }

    private HttpResponse<byte[]> get(String path, String acceptEncoding)
            throws IOException, InterruptedException {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort + path)).GET();
        if (acceptEncoding != null) {
            request.header("Accept-Encoding", acceptEncoding);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String gunzip(byte[] body) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
