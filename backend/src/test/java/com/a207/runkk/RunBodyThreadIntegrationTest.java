package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.DomainEventPublisher;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.SettingKey;
import com.a207.runkk.runbody.RunBodyService;
import com.a207.runkk.runbody.RunBodySessionView;
import com.a207.runkk.runbody.RunBodyStatus;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.running.RunUploadCommand;
import com.a207.runkk.running.RunUploadService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 14장 통합 — 런바디 스레드(05-19 7절 2, S-31). 실 PostgreSQL·Redis(V38 runbody_job)와 ai 서버 흉내(내장 HTTP 서버)
 * 위에서: 유효 러닝 저장 → 커밋 뒤 비동기 제출(POST /sessions/{id}/analyze) → pollDue가 상태 조회 → DONE → sessionOf가 ai
 * 본문을 그대로 → 월간 집계, ai 503 뒤 재제출, 탈퇴 시 작업 삭제.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("런바디 스레드 통합(S-31)")
class RunBodyThreadIntegrationTest {

    private static final String TOKEN = "it-internal-token";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger NAMES = new AtomicInteger();

    /** 세션 ID → ai 상태(pending·done). */
    private static final Map<String, String> AI_STATUS = new ConcurrentHashMap<>();

    private static final Map<String, String> SUBMITTED = new ConcurrentHashMap<>();
    private static final AtomicReference<String> LAST_TOKEN = new AtomicReference<>();
    private static final AtomicInteger ANALYZE_FAILURES = new AtomicInteger();
    private static final HttpServer AI = startAiStub();

    @DynamicPropertySource
    static void aiServer(DynamicPropertyRegistry registry) {
        registry.add("runkk.ai.server-url", () -> "http://localhost:" + AI.getAddress().getPort());
        registry.add("runkk.ai.internal-token", () -> TOKEN);
    }

    @AfterAll
    static void stopAi() {
        AI.stop(0);
    }

    private static HttpServer startAiStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.createContext("/sessions", RunBodyThreadIntegrationTest::sessions);
            server.createContext("/users", RunBodyThreadIntegrationTest::months);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sessions(HttpExchange exchange) throws IOException {
        LAST_TOKEN.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
        String[] parts = exchange.getRequestURI().getPath().split("/");
        String id = parts[2];
        if (exchange.getRequestMethod().equals("POST")) {
            String body =
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (ANALYZE_FAILURES.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                reply(exchange, 503, "{\"reason\":\"ENGINE_BUSY\"}");
                return;
            }
            SUBMITTED.put(id, body);
            AI_STATUS.putIfAbsent(id, "pending");
            reply(exchange, 202, "{\"sessionId\":\"" + id + "\",\"status\":\"pending\"}");
            return;
        }
        String status = AI_STATUS.get(id);
        if (status == null) {
            reply(exchange, 404, "{\"reason\":\"SESSION_NOT_FOUND\"}");
        } else if (parts.length == 4 && parts[3].equals("status")) {
            reply(
                    exchange,
                    200,
                    "{\"sessionId\":\"%s\",\"status\":\"%s\",\"updatedAt\":\"2026-11-07T00:00:00Z\",\"errorCode\":null}"
                            .formatted(id, status));
        } else if (status.equals("done")) {
            reply(exchange, 200, result(id, userOf(id)));
        } else {
            reply(exchange, 409, "{\"reason\":\"ANALYSIS_PENDING\"}");
        }
    }

    private static void months(HttpExchange exchange) throws IOException {
        String[] parts = exchange.getRequestURI().getPath().split("/");
        reply(
                exchange,
                200,
                "{\"userId\":\"%s\",\"yearMonth\":\"%s\",\"sessionCount\":1,\"axes\":{}}"
                        .formatted(parts[2], parts[4]));
    }

    private static String userOf(String id) {
        try {
            return JSON.readTree(SUBMITTED.get(id)).path("userId").asText();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String result(String id, String userId) {
        return ("{\"sessionId\":\"%s\",\"userId\":\"%s\",\"schemaVersion\":2,\"engineVersion\":\"0.2\","
                    + "\"summary\":{\"distanceMeters\":5000,\"paceSecondsPerKm\":300},"
                    + "\"axes\":{\"fitness\":{\"score\":70,\"ref\":55,\"value\":null,\"label\":\"보통\",\"level\":\"mid\"}}}")
                .formatted(id, userId);
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Autowired MemberRepository members;
    @Autowired RunUploadService uploads;
    @Autowired RunBodyService runBody;
    @Autowired OperationSettingService settings;
    @Autowired DomainEventPublisher events;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.a207.runkk.member.ProfileService profiles;

    @BeforeEach
    void clean() {
        for (String table :
                List.of(
                        "runbody_job",
                        "report",
                        "reward_accrual",
                        "reward_wallet",
                        "course_verdict",
                        "runimal_profile",
                        "notification",
                        "personal_best",
                        "personal_record_book",
                        "run_record",
                        "member_consent",
                        "member")) {
            jdbc.update("delete from " + table);
        }
        AI_STATUS.clear();
        SUBMITTED.clear();
        ANALYZE_FAILURES.set(0);
        settings.change("it", SettingKey.RUNBODY_ENABLED, "true");
    }

    private long member() {
        String name = "런바디러너" + NAMES.incrementAndGet();
        return tx.execute(
                s ->
                        members.save(MemberFixtures.member(name, LocalDateTime.now().minusDays(30)))
                                .getId());
    }

    private String upload(long memberId) {
        String id = UUID.randomUUID().toString();
        uploads.upload(
                memberId,
                new RunUploadCommand(
                        RunFixtures.upload(
                                id, LocalDateTime.now().minusHours(1).withNano(0), 5_000, 1_500)));
        return id;
    }

    private String jobStatus(String recordId) {
        List<String> found =
                jdbc.queryForList(
                        "select status from runbody_job where record_id = ?",
                        String.class,
                        recordId);
        return found.isEmpty() ? null : found.get(0);
    }

    private String awaitStatus(String recordId, String expected) throws InterruptedException {
        String status = jobStatus(recordId);
        for (int i = 0; i < 100 && !expected.equals(status); i++) {
            Thread.sleep(100);
            status = jobStatus(recordId);
        }
        return status;
    }

    /** 다음 조회 시각을 지금으로 당긴다(5초·30초 대기 대신). */
    private void makeDue(String recordId) {
        jdbc.update(
                "update runbody_job set next_poll_at = ? where record_id = ?",
                LocalDateTime.now().minusSeconds(1),
                recordId);
    }

    @Test
    void s31_savedRun_isSubmittedPolledAndRead() throws Exception {
        long m = member();
        String recordId = upload(m);

        assertThat(awaitStatus(recordId, "ANALYZING")).isEqualTo("ANALYZING");
        assertThat(LAST_TOKEN.get()).isEqualTo(TOKEN);
        JsonNode sent = JSON.readTree(SUBMITTED.get(recordId));
        assertThat(sent.path("sessionId").asText()).isEqualTo(recordId);
        assertThat(sent.path("userId").asText()).isEqualTo(Long.toString(m));
        assertThat(sent.path("source").asText()).isEqualTo("phone");
        assertThat(sent.path("activeMs").asLong()).isEqualTo(1_500_000);
        assertThat(sent.path("profile").path("birthYear").asInt()).isEqualTo(1995);
        assertThat(sent.path("gps").size()).isEqualTo(1_501);
        assertThat(sent.path("samples").size()).isEqualTo(1_500);
        assertThat(sent.toString()).doesNotContain("런바디러너");
        assertThat(runBody.sessionOf(m, recordId).status()).isEqualTo(RunBodyStatus.ANALYZING);

        makeDue(recordId);
        runBody.pollDue();
        assertThat(jobStatus(recordId)).isEqualTo("ANALYZING");

        AI_STATUS.put(recordId, "done");
        makeDue(recordId);
        runBody.pollDue();
        assertThat(jobStatus(recordId)).isEqualTo("DONE");
        assertThat(
                        jdbc.queryForObject(
                                "select overall_score from runbody_job where record_id = ?",
                                Integer.class,
                                recordId))
                .as("V42: DONE이 되면 ai 결과의 러닝 점수(점수 있는 축 평균)를 남긴다(G-30)")
                .isEqualTo(70);
        assertThat(runBody.sessionsOf(m, null, 20).items())
                .singleElement()
                .satisfies(item -> assertThat(item.score()).isEqualTo(70));

        RunBodySessionView view = runBody.sessionOf(m, recordId);
        assertThat(view.status()).isEqualTo(RunBodyStatus.DONE);
        assertThat(JSON.readTree(view.result()))
                .isEqualTo(JSON.readTree(result(recordId, Long.toString(m))));

        String month = runBody.monthOf(m, YearMonth.of(2026, 11)).body();
        assertThat(JSON.readTree(month).path("userId").asText()).isEqualTo(Long.toString(m));
    }

    @Test
    void aiBusy_isResubmittedByPollDue() throws Exception {
        ANALYZE_FAILURES.set(1);
        long m = member();
        String recordId = upload(m);
        for (int i = 0; i < 100; i++) {
            Integer attempts =
                    jdbc.queryForObject(
                            "select coalesce(max(attempts), 0) from runbody_job where record_id ="
                                    + " ?",
                            Integer.class,
                            recordId);
            if (attempts != null && attempts == 1) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(jobStatus(recordId)).isEqualTo("PENDING");

        makeDue(recordId);
        runBody.pollDue();
        assertThat(jobStatus(recordId)).isEqualTo("ANALYZING");
        assertThat(SUBMITTED).containsKey(recordId);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "e2e 1: 출생연도 없는 회원은 BIRTH_YEAR_MISSING으로 남고(V44), 프로필에 출생연도를 넣으면 다시 제출된다")
    void missingBirthYear_isResubmittedAfterProfileEdit() throws Exception {
        long m = member();
        jdbc.update("update member set birth_year = null where id = ?", m);
        String recordId = upload(m);
        assertThat(awaitStatus(recordId, "FAILED")).isEqualTo("FAILED");
        assertThat(
                        jdbc.queryForObject(
                                "select failure_reason from runbody_job where record_id = ?",
                                String.class,
                                recordId))
                .isEqualTo("BIRTH_YEAR_MISSING");
        assertThat(runBody.sessionOf(m, recordId).failureReason()).isEqualTo("BIRTH_YEAR_MISSING");
        assertThat(SUBMITTED).doesNotContainKey(recordId);

        profiles.editProfile(m, new com.a207.runkk.member.ProfileEdit(null, 1995, null, null));
        assertThat(awaitStatus(recordId, "PENDING")).isEqualTo("PENDING");
        runBody.pollDue();

        assertThat(jobStatus(recordId)).isEqualTo("ANALYZING");
        assertThat(SUBMITTED).containsKey(recordId);
    }

    @Test
    void disabled_createsNoJob_andCallsNoAi() throws Exception {
        settings.change("it", SettingKey.RUNBODY_ENABLED, "false");
        long m = member();
        String recordId = upload(m);
        Thread.sleep(500);
        assertThat(jobStatus(recordId)).isNull();
        assertThat(SUBMITTED).doesNotContainKey(recordId);
    }

    @Test
    void d448_withdrawal_deletesJobs() throws Exception {
        long m = member();
        String recordId = upload(m);
        assertThat(awaitStatus(recordId, "ANALYZING")).isEqualTo("ANALYZING");

        tx.executeWithoutResult(s -> events.publish(new MemberWithdrawn(m, LocalDateTime.now())));

        assertThat(jobStatus(recordId)).isNull();
    }
}
