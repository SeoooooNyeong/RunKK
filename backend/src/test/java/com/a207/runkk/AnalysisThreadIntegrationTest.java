package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.analysis.AnalysisWithdrawalListener;
import com.a207.runkk.analysis.CoachService;
import com.a207.runkk.analysis.DailyPlan;
import com.a207.runkk.analysis.LlmCallQuota;
import com.a207.runkk.analysis.QuotaCounter;
import com.a207.runkk.analysis.RankingKind;
import com.a207.runkk.analysis.RankingService;
import com.a207.runkk.analysis.ReportService;
import com.a207.runkk.analysis.ReportView;
import com.a207.runkk.analysis.RunningGoalRepository;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.Distance;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.member.MemberSettingService;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.policy.LlmCallLimit;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.running.RunRecordSaved;
import com.a207.runkk.running.RunUploadCommand;
import com.a207.runkk.running.RunUploadService;
import com.a207.runkk.running.UploadResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 14장 통합 — 스레드 기반(B5 범위). 실 PostgreSQL·Redis와 ai 서버 흉내(내장 HTTP 서버) 위에서: T-8 리포트 스레드(SQ-16, 유효 러닝 저장
 * → 커밋 뒤 비동기 리포트 → AI 코멘트, LLM 실패·3초 초과·미동의는 규칙 문구), T-16 AI 코멘트(같은 사건 재수신에 코멘트 유지, 회원·전체 한도 동시 경합,
 * ai 요청에 회원 식별자 0 NFR-024), infra.analysis(스냅숏 저장·Redis 캐시·DB 대체, 계획 회원 조회, 탈퇴 삭제, 한도 키 TTL).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@org.junit.jupiter.api.DisplayName("리포트·AI 코멘트·랭킹 통합(T-8, T-16)")
class AnalysisThreadIntegrationTest {

    private static final String TOKEN = "it-internal-token";
    private static final String AI_TEXT = "꾸준히 늘고 있어요!";
    private static final int POLL_MILLIS = 100;
    private static final int POLL_TIMES = 150;
    private static final long SLOW_MILLIS = 3_500;
    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final ObjectMapper JSON = new ObjectMapper();

    /** ai 서버 흉내의 응답 방식. */
    enum Mode {
        OK,
        SLOW,
        FAIL
    }

    private static final AtomicReference<Mode> MODE = new AtomicReference<>(Mode.OK);
    private static final AtomicInteger HITS = new AtomicInteger();
    private static final AtomicReference<String> LAST_BODY = new AtomicReference<>();
    private static final AtomicReference<String> LAST_TOKEN = new AtomicReference<>();
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
            server.createContext(
                    "/comments",
                    exchange -> {
                        HITS.incrementAndGet();
                        LAST_TOKEN.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
                        LAST_BODY.set(
                                new String(
                                        exchange.getRequestBody().readAllBytes(),
                                        StandardCharsets.UTF_8));
                        Mode mode = MODE.get();
                        if (mode == Mode.SLOW) {
                            sleep(SLOW_MILLIS);
                        }
                        int status = mode == Mode.FAIL ? 503 : 200;
                        String body =
                                mode == Mode.FAIL
                                        ? "{\"reason\":\"LLM_UNAVAILABLE\"}"
                                        : "{\"text\":\"" + AI_TEXT + "\"}";
                        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        try {
                            exchange.sendResponseHeaders(status, bytes.length);
                            try (OutputStream out = exchange.getResponseBody()) {
                                out.write(bytes);
                            }
                        } catch (IOException e) {
                            // 클라이언트가 3초에서 끊었다(시간 초과 시험).
                        }
                    });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Autowired MemberRepository members;
    @Autowired MemberSettingService memberSettings;
    @Autowired RunUploadService uploads;
    @Autowired ReportService reports;
    @Autowired RankingService rankings;
    @Autowired CoachService coach;
    @Autowired RunningGoalRepository goals;
    @Autowired AnalysisWithdrawalListener withdrawal;
    @Autowired LlmCallQuota quota;
    @Autowired QuotaCounter counter;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        for (String table :
                List.of(
                        "report",
                        "daily_plan",
                        "weekly_goal",
                        "running_goal",
                        "ranking_entry",
                        "ranking_snapshot",
                        "race_contribution",
                        "weekly_race",
                        "crew_membership",
                        "crew",
                        "reward_accrual",
                        "owned_item",
                        "reward_wallet",
                        "course_verdict",
                        "runimal_profile",
                        "notification",
                        "personal_best",
                        "personal_record_book",
                        "run_record",
                        "member_consent",
                        "member",
                        "audit_record")) {
            jdbc.update("delete from " + table);
        }
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
        MODE.set(Mode.OK);
        HITS.set(0);
    }

    private long member(boolean llmAgreed) {
        String name = "분석러너" + NAMES.incrementAndGet();
        long id =
                tx.execute(
                        s ->
                                members.save(
                                                MemberFixtures.member(
                                                        name, LocalDateTime.now().minusDays(30)))
                                        .getId());
        if (llmAgreed) {
            memberSettings.giveConsent(id, ConsentItem.LLM_TRANSFER);
        }
        return id;
    }

    private UploadResult upload(long memberId, int daysAgo) {
        return uploads.upload(
                memberId,
                new RunUploadCommand(
                        RunFixtures.upload(
                                UUID.randomUUID().toString(),
                                LocalDateTime.now().minusDays(daysAgo).withHour(7),
                                5_000,
                                1_500)));
    }

    private ReportView awaitReady(long memberId) {
        ReportView view = reports.report(memberId);
        for (int i = 0; i < POLL_TIMES && view.status() != ReportView.Status.READY; i++) {
            sleep(POLL_MILLIS);
            view = reports.report(memberId);
        }
        return view;
    }

    @Test
    void t8_validRun_getsAiComment_withSummaryOnly() throws Exception {
        long m = member(true);
        upload(m, 0);
        ReportView view = awaitReady(m);
        assertThat(view.status()).isEqualTo(ReportView.Status.READY);
        assertThat(view.comment()).isEqualTo(AI_TEXT);
        assertThat(view.commentSource().name()).isEqualTo("AI");
        assertThat(HITS.get()).isEqualTo(1);
        assertThat(LAST_TOKEN.get()).isEqualTo(TOKEN);
        JsonNode sent = JSON.readTree(LAST_BODY.get());
        List<String> fields = new ArrayList<>();
        sent.fieldNames().forEachRemaining(fields::add);
        assertThat(fields)
                .containsExactlyInAnyOrder(
                        "averageScore",
                        "distanceChangeMeters",
                        "paceChangeSeconds",
                        "frequentDay",
                        "frequentHourBand",
                        "runCount");
        assertThat(sent.get("runCount").asInt()).isEqualTo(1);
        assertThat(LAST_BODY.get()).doesNotContain("memberId").doesNotContain("분석러너");
    }

    @Test
    void t8_llmFailureTimeoutOrNoConsent_fallsBackToRule() {
        MODE.set(Mode.FAIL);
        long failed = member(true);
        upload(failed, 0);
        assertThat(awaitReady(failed).commentSource().name()).isEqualTo("RULE");

        MODE.set(Mode.SLOW);
        long slow = member(true);
        long started = System.nanoTime();
        upload(slow, 0);
        ReportView timedOut = awaitReady(slow);
        assertThat(timedOut.commentSource().name()).isEqualTo("RULE");
        assertThat(Duration.ofNanos(System.nanoTime() - started))
                .isLessThan(Duration.ofMillis(SLOW_MILLIS + 2_000));

        MODE.set(Mode.OK);
        int before = HITS.get();
        long silent = member(false);
        upload(silent, 0);
        assertThat(awaitReady(silent).commentSource().name()).isEqualTo("RULE");
        assertThat(HITS.get()).isEqualTo(before);
    }

    @Test
    void t16_sameEventRedelivered_keepsCommentAndDoesNotCallAiAgain() {
        long m = member(true);
        UploadResult result = upload(m, 0);
        assertThat(awaitReady(m).comment()).isEqualTo(AI_TEXT);
        int hits = HITS.get();
        RunRecordSaved again =
                new RunRecordSaved(
                        result.recordId(),
                        m,
                        LocalDateTime.now().withHour(7),
                        Distance.ofKm(5),
                        Duration.ofMinutes(25),
                        true,
                        38.3,
                        com.a207.runkk.running.RunMode.FREE,
                        null,
                        null,
                        true,
                        1,
                        LocalDateTime.now());
        reports.onRunSaved(again);
        sleep(POLL_MILLIS * 10L);
        ReportView view = reports.report(m);
        assertThat(view.comment()).isEqualTo(AI_TEXT);
        assertThat(view.status()).isEqualTo(ReportView.Status.READY);
        assertThat(HITS.get()).isEqualTo(hits);

        upload(m, 1);
        ReportView second = awaitReadyFor(m, 2);
        assertThat(second.commentSource().name()).as("회원 하루 1회 뒤 규칙 문구").isEqualTo("RULE");
    }

    private ReportView awaitReadyFor(long memberId, int toRun) {
        ReportView view = reports.report(memberId);
        for (int i = 0; i < POLL_TIMES; i++) {
            if (view.toRun() != null
                    && view.toRun() == toRun
                    && view.status() == ReportView.Status.READY) {
                return view;
            }
            sleep(POLL_MILLIS);
            view = reports.report(memberId);
        }
        return view;
    }

    @Test
    void t16_memberAndGlobalLimits_holdUnderRaceOnRedis() throws Exception {
        LocalDate day = LocalDate.of(2099, 1, 1);
        LlmCallLimit limit = new LlmCallLimit(1, 5);
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            long memberId = 1_000 + i % 20;
            attempts.add(() -> quota.tryAcquire(memberId, day, limit));
        }
        assertThat(together(attempts)).isEqualTo(5);

        List<Callable<Boolean>> sameMember = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            sameMember.add(() -> quota.tryAcquire(7, day.plusDays(1), new LlmCallLimit(1, 200)));
        }
        assertThat(together(sameMember)).isEqualTo(1);

        assertThat(
                        counter.tryIncrementAll(
                                List.of(new QuotaCounter.Limit("runkk:it:ttl", 1)),
                                Duration.ofHours(48)))
                .isTrue();
        assertThat(redis.getExpire("runkk:it:ttl")).isPositive();
        assertThat(
                        counter.tryIncrementAll(
                                List.of(
                                        new QuotaCounter.Limit("runkk:it:a", 5),
                                        new QuotaCounter.Limit("runkk:it:ttl", 1)),
                                Duration.ofHours(48)))
                .as("두 번째 키가 넘으면 첫 키도 되돌린다")
                .isFalse();
        assertThat(redis.opsForValue().get("runkk:it:a")).isEqualTo("0");
    }

    private static int together(List<Callable<Boolean>> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(work.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> w : work) {
            futures.add(
                    pool.submit(
                            () -> {
                                go.await();
                                return w.call();
                            }));
        }
        go.countDown();
        int accepted = 0;
        for (Future<Boolean> f : futures) {
            accepted += f.get() ? 1 : 0;
        }
        pool.shutdown();
        return accepted;
    }

    @Test
    void rankingSnapshots_areReplacedCachedAndReadBackFromDb() {
        long a = member(false);
        long b = member(false);
        upload(a, 0);
        upload(b, 0);
        upload(b, 0);
        rankings.rebuild(LocalDateTime.now());
        rankings.rebuild(LocalDateTime.now());
        assertThat(jdbc.queryForObject("select count(*) from ranking_snapshot", Integer.class))
                .isEqualTo(4);

        assertThat(redis.hasKey("ranking:" + RankingKind.PERSONAL + ":" + AggregationPeriod.WEEKLY))
                .isTrue();
        assertThat(rankings.personalRanking(b, AggregationPeriod.WEEKLY).mine().rank())
                .isEqualTo(1);
        com.a207.runkk.analysis.RankingView cached =
                rankings.personalRanking(a, AggregationPeriod.WEEKLY);
        assertThat(cached.mine().rank()).as("캐시의 회원별 줄").isEqualTo(2);
        assertThat(cached.gapToNext()).isEqualTo(Distance.ofKm(5));
        assertThat(redis.keys("ranking:PERSONAL:WEEKLY:*:subjects")).isNotEmpty();
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
        com.a207.runkk.analysis.RankingView fromDb =
                rankings.personalRanking(a, AggregationPeriod.WEEKLY);
        assertThat(fromDb.mine().rank()).isEqualTo(2);
        assertThat(fromDb.gapToNext()).isEqualTo(Distance.ofKm(5));
        assertThat(fromDb.aggregatedAt()).isNotNull();
    }

    @Test
    void plansAndWithdrawal_useOwnTablesOnly() {
        long m = member(false);
        LocalDate today = LocalDate.now();
        coach.putPlan(m, new DailyPlan(today, Distance.ofKm(3), null));
        List<Long> planned = tx.execute(s -> goals.membersWithPlanOn(today));
        assertThat(planned).containsExactly(m);
        assertThat(coach.remindMissedPlans(LocalDateTime.now())).isEqualTo(1);
        assertThat(coach.purgePastPlans(today)).as("보존 기간 안 계획은 남는다").isZero();
        assertThat(coach.purgePastPlans(today.plusYears(1).plusDays(1))).isEqualTo(1);
        coach.putPlan(m, new DailyPlan(today, Distance.ofKm(3), null));
        upload(m, 0);
        awaitReady(m);
        tx.executeWithoutResult(
                s -> withdrawal.onMemberWithdrawn(new MemberWithdrawn(m, LocalDateTime.now())));
        assertThat(jdbc.queryForObject("select count(*) from daily_plan", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from report", Integer.class)).isZero();
    }
}
