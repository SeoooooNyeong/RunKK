package com.a207.runkk.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.SettingKey;
import com.a207.runkk.web.WebTestSupport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class WriterModelAdminTest extends WebTestSupport {
    private static final String PATH = "/api/v1/admin/models";
    private static final String WRITER = "http://writer:8080";
    private static final String TOKEN = "test-writer-registry-token";
    private static final String MODEL =
            """
            {"modelId":"writer-v1","baseUrl":"http://writer:8080","note":"기준",
             "active":true,"registeredAt":"2026-11-09T00:00:00+00:00",
             "activatedAt":"2026-11-09T00:00:00+00:00"}
            """;
    private static final LinkedBlockingQueue<Reply> REPLIES = new LinkedBlockingQueue<>();
    private static final LinkedBlockingQueue<Request> REQUESTS = new LinkedBlockingQueue<>();
    private static HttpServer upstream;

    @Autowired OperationSettingService settings;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void aiServer(DynamicPropertyRegistry properties) throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/models", WriterModelAdminTest::respond);
        upstream.start();
        properties.add(
                "runkk.ai.server-url", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
        properties.add("runkk.ai.internal-token", () -> TOKEN);
    }

    @AfterAll
    static void closeServer() {
        if (upstream != null) {
            upstream.stop(0);
        }
    }

    @BeforeEach
    void reset() {
        REPLIES.clear();
        REQUESTS.clear();
        settings.change(
                "test",
                SettingKey.OPERATOR_WHITELIST,
                "{\"accounts\":{\"KAKAO\":[\"writer-op\"]}}");
    }

    @Test
    void requiresOperatorAndRejectsUnapprovedDestination() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isForbidden());
        mvc.perform(as(signUp(), get(PATH))).andExpect(status().isForbidden());
        mvc.perform(register(operatorBearer(), "http://postgres:5432"))
                .andExpect(status().isBadRequest());
        assertThat(REQUESTS).isEmpty();
    }

    @Test
    void forwardsWriterContractsAndAuditsOperatorMutations() throws Exception {
        String operator = operatorBearer();
        enqueue(200, "{\"models\":[],\"active\":null}");
        mvc.perform(get(PATH).header("Authorization", operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models").isEmpty());
        assertRequest("GET", "/models");

        enqueue(201, MODEL);
        mvc.perform(register(operator, WRITER + "/"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true));
        assertThat(body(assertRequest("POST", "/models").body()))
                .isEqualTo(
                        body(
                                "{\"modelId\":\"writer-v1\",\"baseUrl\":\"http://writer:8080\",\"note\":\"기준\"}"));

        enqueue(201, MODEL.replace("기준", "다시 등록"));
        mvc.perform(register(operator, WRITER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.note").value("다시 등록"));
        assertRequest("POST", "/models");

        enqueue(200, MODEL);
        mvc.perform(post(PATH + "/writer-v1/activate").header("Authorization", operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelId").value("writer-v1"));
        assertRequest("POST", "/models/writer-v1/activate");

        enqueue(200, MODEL);
        mvc.perform(post(PATH + "/rollback").header("Authorization", operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        assertRequest("POST", "/models/rollback");

        assertThat(
                        jdbc.queryForList(
                                "select action from audit_record where actor_id = ? and target_id ="
                                        + " ?",
                                String.class,
                                "KAKAO:writer-op",
                                "writer-v1"))
                .contains(
                        "WRITER_MODEL_REGISTER", "WRITER_MODEL_ACTIVATE", "WRITER_MODEL_ROLLBACK");
    }

    @Test
    void exposesOnlyContractErrorsAndRejectsInvalidAiResponses() throws Exception {
        String operator = operatorBearer();
        enqueue(404, "{\"reason\":\"MODEL_NOT_FOUND\"}");
        mvc.perform(post(PATH + "/missing/activate").header("Authorization", operator))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODEL_NOT_FOUND"));
        enqueue(409, "{\"reason\":\"NO_PREVIOUS_MODEL\"}");
        mvc.perform(post(PATH + "/rollback").header("Authorization", operator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_PREVIOUS_MODEL"));
        enqueue(503, "{\"reason\":\"internal database details\"}");
        mvc.perform(get(PATH).header("Authorization", operator))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MODEL_REGISTRY_UNAVAILABLE"));
        enqueue(200, "{}");
        mvc.perform(get(PATH).header("Authorization", operator))
                .andExpect(status().isServiceUnavailable());
        assertThat(
                        jdbc.queryForList(
                                "select after_value from audit_record where actor_id = ? and"
                                        + " target_id = ?",
                                String.class,
                                "KAKAO:writer-op",
                                "missing"))
                .containsExactly("REQUESTED");
    }

    @Test
    void hidesCredentialsInLegacyRegistryUrls() throws Exception {
        String model =
                MODEL.replace(
                        "http://writer:8080",
                        "http://legacy-user:private@writer:8080/llm?token=private#private");
        enqueue(200, "{\"models\":[" + model + "],\"active\":\"writer-v1\"}");
        mvc.perform(get(PATH).header("Authorization", operatorBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models[0].baseUrl").value("http://writer:8080/llm"));
    }

    private String operatorBearer() throws Exception {
        String result =
                mvc.perform(
                                post("/api/v1/admin/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                write(
                                                        Map.of(
                                                                "provider",
                                                                "KAKAO",
                                                                "providerToken",
                                                                "writer-op"))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return "Bearer " + body(result).path("adminToken").asText();
    }

    private MockHttpServletRequestBuilder register(String operator, String url) throws Exception {
        return post(PATH)
                .header("Authorization", operator)
                .contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("modelId", "writer-v1", "baseUrl", url, "note", "기준")));
    }

    private static void enqueue(int status, String body) {
        REPLIES.add(new Reply(status, body));
    }

    private static Request assertRequest(String method, String path) {
        Request request = REQUESTS.poll();
        assertThat(request).isNotNull();
        assertThat(request.method()).isEqualTo(method);
        assertThat(request.path()).isEqualTo(path);
        assertThat(request.token()).isEqualTo(TOKEN);
        return request;
    }

    private static void respond(HttpExchange exchange) throws IOException {
        try {
            REQUESTS.add(
                    new Request(
                            exchange.getRequestMethod(),
                            exchange.getRequestURI().getRawPath(),
                            exchange.getRequestHeaders().getFirst("X-Internal-Token"),
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8)));
            Reply reply = REPLIES.poll();
            if (reply == null) {
                exchange.sendResponseHeaders(500, -1);
                return;
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
        } finally {
            exchange.close();
        }
    }

    private record Reply(int status, String body) {}

    private record Request(String method, String path, String token, String body) {}
}
