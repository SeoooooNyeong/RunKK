package com.a207.runkk.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

/**
 * 모니터링(S15P31A207-78): Prometheus가 읽는 /actuator/prometheus는 관리 포트에만 있다. 배포(develop·prod)는 관리 포트를
 * 서비스 포트와 나눠 compose 내부 망에서만 읽게 하고, nginx는 바깥에 /actuator/health 하나만 넘긴다. 시험은 지표 내보내기를 기본으로 끄므로
 * {@code @AutoConfigureObservability}로 켠다.
 */
@AutoConfigureObservability(tracing = false)
@SpringBootTest(
        classes = RunkkApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@ActiveProfiles("test")
@DisplayName("관리 포트 actuator(모니터링)")
class ManagementEndpointTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort int serverPort;
    @LocalManagementPort int managementPort;

    @Test
    @DisplayName("관리 포트는 인증 없이 Prometheus 지표를 내고 application 태그를 붙인다")
    void prometheus_isServedOnManagementPort() throws Exception {
        get(serverPort, "/api/v1/legal/terms");

        HttpResponse<String> response = get(managementPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("jvm_memory_used_bytes")
                .contains("http_server_requests_seconds_bucket")
                .contains("application=\"runkk\"");
    }

    @Test
    @DisplayName("서비스 포트에는 Prometheus 지표가 없다")
    void prometheus_isNotOnServicePort() throws Exception {
        assertThat(get(serverPort, "/actuator/prometheus").statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("상태 확인은 관리 포트에서 공개다(compose healthcheck·nginx /actuator/health)")
    void health_isOnManagementPort() throws Exception {
        HttpResponse<String> readiness = get(managementPort, "/actuator/health/readiness");

        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(readiness.body()).contains("UP");
    }

    @Test
    @DisplayName("develop·prod 프로필은 관리 포트를 8081로 나눈다")
    void deployProfiles_useSeparateManagementPort() throws IOException {
        List<PropertySource<?>> documents =
                new YamlPropertySourceLoader()
                        .load("application", new ClassPathResource("application.yml"));

        for (String profile : List.of("develop", "prod")) {
            PropertySource<?> document =
                    documents.stream()
                            .filter(
                                    source ->
                                            profile.equals(
                                                    String.valueOf(
                                                            source.getProperty(
                                                                    "spring.config.activate.on-profile"))))
                            .findFirst()
                            .orElseThrow();
            assertThat(String.valueOf(document.getProperty("management.server.port")))
                    .as(profile)
                    .isEqualTo("${MANAGEMENT_PORT:8081}");
        }
    }

    private static HttpResponse<String> get(int port, String path)
            throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
