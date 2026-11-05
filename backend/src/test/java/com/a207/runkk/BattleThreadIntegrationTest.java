package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.battle.Battle;
import com.a207.runkk.battle.BattleClockService;
import com.a207.runkk.battle.BattleRepository;
import com.a207.runkk.battle.BattleResultService;
import com.a207.runkk.battle.BattleResultView;
import com.a207.runkk.battle.BattleStartupListener;
import com.a207.runkk.battle.BattleStatus;
import com.a207.runkk.battle.BattleType;
import com.a207.runkk.battle.LiveBattleService;
import com.a207.runkk.battle.LiveBattleSession;
import com.a207.runkk.battle.LiveSessionStore;
import com.a207.runkk.battle.MatchQueue;
import com.a207.runkk.battle.MatchTicket;
import com.a207.runkk.battle.MatchingService;
import com.a207.runkk.battle.Matchmaker;
import com.a207.runkk.battle.OpponentKind;
import com.a207.runkk.battle.Verdict;
import com.a207.runkk.battle.VirtualRecordRepository;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ProgressReport;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.SettingKey;
import com.a207.runkk.running.CardKind;
import com.a207.runkk.running.LocationPoint;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunUpload;
import com.a207.runkk.running.RunUploadCommand;
import com.a207.runkk.running.RunUploadService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 14장 통합 — 스레드 기반(B3 범위). 실 PostgreSQL·Redis 위에서: T-2 랜덤 대결 스레드(MatchingService → Matchmaker →
 * Battle → BattleStartService → LiveBattleService → BattleClockService → BattleResultService, 실기기
 * 부분 제외)와 10쌍 동시 매칭, T-3 고스트 스레드(MemberQueryService·RunQueryService 조합 시간 측정), T-11 운영 기준 변경 → 가상
 * 기록 재생성, T-14 서버 시작(D-55).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@org.junit.jupiter.api.DisplayName("대결 스레드 통합(T-2, T-3, T-11, T-14)")
class BattleThreadIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BattleThreadIntegrationTest.class);
    private static final int PAIRS = 10;
    private static final Duration GHOST_BUDGET = Duration.ofSeconds(3);

    @Autowired MatchingService matching;
    @Autowired Matchmaker matchmaker;
    @Autowired BattleClockService battleClock;
    @Autowired LiveBattleService live;
    @Autowired BattleResultService results;
    @Autowired BattleRepository battles;
    @Autowired MatchQueue queue;
    @Autowired LiveSessionStore sessions;
    @Autowired VirtualRecordRepository virtuals;
    @Autowired BattleStartupListener startup;
    @Autowired OperationSettingService settings;
    @Autowired RunUploadService uploads;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @MockitoSpyBean RunQueryService runQueries;

    /** 기록 반영 두 건이 대결을 읽은 뒤 서로를 기다리게 하는 문(M3 재현). 비어 있으면 그냥 지나간다. */
    private final AtomicReference<CountDownLatch> recordGate = new AtomicReference<>();

    @BeforeEach
    void clean() {
        recordGate.set(null);
        org.mockito.Mockito.doAnswer(
                        inv -> {
                            CountDownLatch gate = recordGate.get();
                            if (gate != null) {
                                gate.countDown();
                                gate.await(5, TimeUnit.SECONDS);
                            }
                            return inv.callRealMethod();
                        })
                .when(runQueries)
                .timeToReach(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());
        for (String table :
                List.of(
                        "battle_disconnection",
                        "battle_participant",
                        "battle",
                        "celebrity_record",
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
    }

    /** 유효 러닝 3회(5km 25분)를 뛰고 배치고사로 골드가 확정된 회원(일반 러닝은 첫 배치를 하지 않는다, J24-6). */
    private long ranked(String nickname) {
        long id =
                tx.execute(
                        s ->
                                members.save(
                                                MemberFixtures.member(
                                                        nickname,
                                                        LocalDateTime.now().minusDays(30)))
                                        .getId());
        LocalDateTime start = LocalDateTime.now().minusDays(3).withNano(0);
        for (int i = 0; i < 3; i++) {
            uploads.upload(
                    id,
                    new RunUploadCommand(
                            RunFixtures.upload(
                                    UUID.randomUUID().toString(),
                                    start.plusHours(i),
                                    5_000,
                                    1_500)));
        }
        tx.executeWithoutResult(
                s -> {
                    var member = members.findById(id).orElseThrow();
                    member.confirmPlacement(Tier.GOLD);
                    members.save(member);
                });
        assertThat(members.findById(id).orElseThrow().getTier()).isEqualTo(Tier.GOLD);
        return id;
    }

    private void uploadBattleRun(long memberId, Battle battle, long meters) {
        LocalDateTime start = battle.getStartAt().orElseThrow();
        int seconds = 1_500;
        List<LocationPoint> points =
                RunFixtures.straightLine(start, (double) meters / seconds, seconds);
        RunUpload upload =
                new RunUpload(
                        UUID.randomUUID().toString(),
                        start,
                        start.plusSeconds(seconds),
                        Distance.ofMeters(meters),
                        Duration.ofSeconds(seconds),
                        RunMode.BATTLE,
                        CardKind.BATTLE_RUN,
                        battle.getId(),
                        null,
                        points.size(),
                        points,
                        List.of(),
                        List.of());
        uploads.upload(memberId, new RunUploadCommand(upload));
    }

    private Battle battleOf(long memberId) {
        Long id =
                jdbc.queryForObject(
                        "select max(b.id) from battle b join battle_participant p on p.battle_id ="
                                + " b.id where p.member_id = ?",
                        Long.class,
                        memberId);
        return load(id);
    }

    private Battle load(long battleId) {
        return tx.execute(
                s -> {
                    Battle battle = battles.findById(battleId).orElseThrow();
                    battle.getParticipants().forEach(p -> p.getDisconnections().size());
                    return battle;
                });
    }

    private static ProgressReport progress(long meters, long seconds, LocalDateTime at) {
        return new ProgressReport(Distance.ofMeters(meters), Duration.ofSeconds(seconds), at);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("T-2: 매칭 → 출발 → 진행 중계·도착 → 카운트다운 종료 → 기록 저장 → 판정·결과")
    void t2_randomBattleThread() {
        long alice = ranked("대결곰");
        long bob = ranked("대결여우");
        matching.requestMatch(alice, StandardDistance.KM5);
        matching.requestMatch(bob, StandardDistance.KM5);
        matchmaker.tick(LocalDateTime.now());

        Battle battle = battleOf(alice);
        assertThat(battle.getType()).isEqualTo(BattleType.RANDOM);
        assertThat(battle.memberIds()).containsExactlyInAnyOrder(alice, bob);
        assertThat(queue.findWaiting(alice)).isEmpty();
        LocalDateTime start = battle.getStartAt().orElseThrow();

        battleClock.tick(start);
        assertThat(sessions.activeBattleIds()).contains(battle.getId());
        live.onProgress(alice, battle.getId(), progress(2_000, 600, start), start.plusMinutes(10));
        live.onProgress(bob, battle.getId(), progress(1_950, 600, start), start.plusMinutes(10));
        LocalDateTime arrival = start.plusMinutes(25);
        live.onProgress(alice, battle.getId(), progress(5_000, 1_500, arrival), arrival);
        assertThat(load(battle.getId()).getStatus()).isEqualTo(BattleStatus.COUNTDOWN);

        battleClock.tick(arrival.plusSeconds(10));
        assertThat(load(battle.getId()).getStatus()).isEqualTo(BattleStatus.RESULT_PENDING);
        assertThat(sessions.activeBattleIds()).doesNotContain(battle.getId());

        uploadBattleRun(alice, battle, 5_000);
        uploadBattleRun(bob, battle, 4_900);
        Battle judged = load(battle.getId());
        assertThat(judged.getStatus()).isEqualTo(BattleStatus.FINISHED);
        BattleResultView view = results.result(alice, battle.getId());
        assertThat(view.verdict()).isEqualTo(Verdict.WIN);
        assertThat(view.corrected()).isFalse();
        assertThat(view.comparison().rows()).as("랜덤 대결은 비교표가 없다(v0.14 D-229)").isEmpty();
        assertThat(results.result(bob, battle.getId()).verdict()).isEqualTo(Verdict.LOSE);
        Integer notifications =
                jdbc.queryForObject(
                        "select count(*) from notification where recipient_id = ? and type ="
                                + " 'OPPONENT_ARRIVED'",
                        Integer.class,
                        bob);
        assertThat(notifications).isEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "M3: 1초 안에 함께 도착한 두 참가자의 기록이 동시에 반영돼도 서로를 보고 판정한다(Battle 낙관적 잠금)")
    void bothRecordsAtOnce_areJudgedTogether() throws Exception {
        long alice = ranked("동시곰");
        long bob = ranked("동시여우");
        matching.requestMatch(alice, StandardDistance.KM5);
        matching.requestMatch(bob, StandardDistance.KM5);
        matchmaker.tick(LocalDateTime.now());
        Battle battle = battleOf(alice);
        LocalDateTime start = battle.getStartAt().orElseThrow();
        battleClock.tick(start);
        LocalDateTime arrival = start.plusMinutes(25);
        live.onProgress(alice, battle.getId(), progress(5_000, 1_500, arrival), arrival);
        live.onProgress(bob, battle.getId(), progress(5_000, 1_500, arrival), arrival);
        battleClock.tick(arrival.plusSeconds(10));
        assertThat(load(battle.getId()).getStatus()).isEqualTo(BattleStatus.RESULT_PENDING);

        recordGate.set(new CountDownLatch(2));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<?>> uploadsInFlight = new ArrayList<>();
        for (long member : List.of(alice, bob)) {
            uploadsInFlight.add(pool.submit(() -> uploadBattleRun(member, battle, 5_000)));
        }
        for (Future<?> f : uploadsInFlight) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        Battle judged = load(battle.getId());
        assertThat(judged.getStatus()).isEqualTo(BattleStatus.FINISHED);
        assertThat(judged.getParticipants())
                .allSatisfy(p -> assertThat(p.getVerified()).contains(true));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "10쌍 동시 매칭: 20명이 동시에 요청해도 한 사람은 한 대결에만 들어간다(FR-082, NFR-004)")
    void tenPairs_concurrentMatching() throws Exception {
        List<Long> runners = new ArrayList<>();
        for (int i = 0; i < PAIRS * 2; i++) {
            runners.add(ranked("동시러너" + i));
        }
        ExecutorService pool = Executors.newFixedThreadPool(PAIRS * 2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> requests = new ArrayList<>();
        for (long id : runners) {
            requests.add(
                    pool.submit(
                            () -> {
                                go.await();
                                return matching.requestMatch(id, StandardDistance.KM5);
                            }));
        }
        go.countDown();
        for (Future<?> f : requests) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        matchmaker.tick(LocalDateTime.now());

        Integer created = jdbc.queryForObject("select count(*) from battle", Integer.class);
        assertThat(created).isEqualTo(PAIRS);
        List<Long> participants =
                jdbc.queryForList("select member_id from battle_participant", Long.class);
        assertThat(participants).hasSize(PAIRS * 2).doesNotHaveDuplicates();
        assertThat(queue.waitingOldestFirst()).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("tryAssignPair 경합: 같은 대기자를 여러 짝이 동시에 노려도 한 번만 배정된다(A-22)")
    void tryAssignPair_isAtomic() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        for (long id = 1; id <= PAIRS + 1; id++) {
            queue.enqueue(MatchTicket.open(id, Tier.GOLD, StandardDistance.KM5, now));
        }
        assertThat(queue.enqueue(MatchTicket.open(1, Tier.GOLD, StandardDistance.KM5, now)))
                .isFalse();
        ExecutorService pool = Executors.newFixedThreadPool(PAIRS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> tries = new ArrayList<>();
        for (long other = 2; other <= PAIRS + 1; other++) {
            long partner = other;
            tries.add(
                    pool.submit(
                            () -> {
                                go.await();
                                return queue.tryAssignPair(1, partner);
                            }));
        }
        go.countDown();
        int assigned = 0;
        for (Future<Boolean> f : tries) {
            assigned += f.get(10, TimeUnit.SECONDS) ? 1 : 0;
        }
        pool.shutdown();
        assertThat(assigned).isEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("T-3: 15초 넘게 기다리면 같은 티어 공개 기록으로 고스트 대결, 조합 시간 측정")
    void t3_ghostThread() {
        long owner = ranked("고스트주인");
        long runner = ranked("고스트도전");
        matching.requestMatch(runner, StandardDistance.KM5);

        long began = System.nanoTime();
        matchmaker.tick(LocalDateTime.now().plusSeconds(16));
        Duration took = Duration.ofNanos(System.nanoTime() - began);
        log.info("ghost_match_took millis={}", took.toMillis());

        Battle ghost = battleOf(runner);
        assertThat(ghost.getType()).isEqualTo(BattleType.GHOST);
        assertThat(ghost.getOpponent().orElseThrow().kind()).isEqualTo(OpponentKind.RUN_RECORD);
        assertThat(ghost.getOpponent().orElseThrow().displayName())
                .isEqualTo(DisplayName.of("고스트주인"));
        assertThat(ghost.isParticipant(owner)).isFalse();
        assertThat(took).isLessThan(GHOST_BUDGET);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "T-11: VIRTUAL_RECORD_RULE·TIER_BOUNDARY 변경 각각 → 가상 기록 100개 재생성")
    void t11_settingChange_regeneratesVirtualRecords() {
        long before = goldFiveKMiddle();
        String rule = SettingKey.VIRTUAL_RECORD_RULE.defaultValue();
        String boundary = SettingKey.TIER_BOUNDARY.defaultValue();
        try {
            settings.change(
                    "op@runkk",
                    SettingKey.VIRTUAL_RECORD_RULE,
                    rule.replace("[-0.05,-0.025,0,0.025,0.05]", "[-0.1,-0.05,0,0.05,0.1]"));
            assertThat(virtuals.count()).isEqualTo(100);
            assertThat(goldFastest()).isLessThan(goldFiveKMiddle() * 92 / 100);

            settings.change(
                    "op@runkk", SettingKey.TIER_BOUNDARY, "{\"lowerBounds\":[31,36,41,46]}");
            assertThat(virtuals.count()).isEqualTo(100);
            assertThat(goldFiveKMiddle()).isLessThan(before);
        } finally {
            settings.change("op@runkk", SettingKey.VIRTUAL_RECORD_RULE, rule);
            settings.change("op@runkk", SettingKey.TIER_BOUNDARY, boundary);
        }
    }

    private long goldFiveKMiddle() {
        return virtuals.findByTierAndDistance(Tier.GOLD, StandardDistance.KM5)
                .get(2)
                .getFinishTime()
                .toMillis();
    }

    private long goldFastest() {
        return virtuals.findByTierAndDistance(Tier.GOLD, StandardDistance.KM5)
                .get(0)
                .getFinishTime()
                .toMillis();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "T-14: 가상 기록 0개·진행 중 세션이 있는 상태로 시작 → 100개, 진행 중 대결 세션만 이어 간다")
    void t14_startup() {
        long alice = ranked("시작곰");
        long bob = ranked("시작여우");
        matching.requestMatch(alice, StandardDistance.KM5);
        matching.requestMatch(bob, StandardDistance.KM5);
        matchmaker.tick(LocalDateTime.now());
        Battle running = battleOf(alice);
        battleClock.tick(running.getStartAt().orElseThrow());
        sessions.save(
                LiveBattleSession.open(
                        987_654L,
                        Distance.ofMeters(1_000),
                        Map.of(1L, DisplayName.of("유령"), 2L, DisplayName.of("유령2")),
                        LocalDateTime.now()));
        tx.executeWithoutResult(s -> virtuals.replaceAll(List.of()));
        assertThat(virtuals.count()).isZero();

        startup.onApplicationReady(null);

        assertThat(virtuals.count()).isEqualTo(100);
        Set<Long> resumed = new HashSet<>(sessions.activeBattleIds());
        assertThat(resumed).containsExactly(running.getId());
    }
}
