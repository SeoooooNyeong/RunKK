package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.battle.Battle;
import com.a207.runkk.battle.BattleClockService;
import com.a207.runkk.battle.BattleRepository;
import com.a207.runkk.battle.BattleResultService;
import com.a207.runkk.battle.BattleStatus;
import com.a207.runkk.battle.BattleType;
import com.a207.runkk.battle.MatchAcceptance;
import com.a207.runkk.battle.MatchQueue;
import com.a207.runkk.battle.MatchingService;
import com.a207.runkk.battle.PlacementReconcileService;
import com.a207.runkk.battle.PlacementRoundListener;
import com.a207.runkk.battle.PlacementRoundReader;
import com.a207.runkk.battle.PlacementRoundState;
import com.a207.runkk.battle.RecordBattleService;
import com.a207.runkk.battle.Side;
import com.a207.runkk.battle.Verdict;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.Member;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.member.PlacementService;
import com.a207.runkk.member.PlacementStatus;
import com.a207.runkk.member.PlacementView;
import com.a207.runkk.member.WithdrawalService;
import com.a207.runkk.running.CardKind;
import com.a207.runkk.running.LocationPoint;
import com.a207.runkk.running.RunFixtures;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunUpload;
import com.a207.runkk.running.RunUploadCommand;
import com.a207.runkk.running.RunUploadService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 배치고사 스레드 통합(05-21 11절 2, SQ-49·SQ-50). 실 PostgreSQL(V35_1까지 Flyway, ddl validate)·Redis 위에서
 * MatchingService → PlacementMatcher → BattleClockService → RunUploadService → BattleResultService
 * → PlacementRoundListener → PlacementService. Docker가 없으면 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@ExtendWith(OutputCaptureExtension.class)
@org.junit.jupiter.api.DisplayName("배치고사 스레드 통합(SQ-49·SQ-50)")
class PlacementThreadIntegrationTest {

    @Autowired MatchingService matching;
    @Autowired MatchQueue queue;
    @Autowired BattleClockService battleClock;
    @Autowired BattleResultService results;
    @Autowired BattleRepository battles;
    @Autowired RecordBattleService recordBattles;
    @Autowired PlacementService placements;
    @Autowired PlacementRoundReader roundReader;
    @Autowired PlacementRoundListener roundListener;
    @Autowired PlacementReconcileService reconcile;
    @Autowired RunUploadService uploads;
    @Autowired MemberRepository members;
    @Autowired MemberQueryService memberQuery;
    @Autowired WithdrawalService withdrawals;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        for (String table :
                List.of(
                        "placement_round",
                        "placement_progress",
                        "battle_disconnection",
                        "battle_participant",
                        "battle",
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

    private long member(String nickname) {
        return tx.execute(
                s ->
                        members.save(
                                        MemberFixtures.member(
                                                nickname, LocalDateTime.now().minusDays(30)))
                                .getId());
    }

    private Battle load(long battleId) {
        return tx.execute(
                s -> {
                    Battle battle = battles.findById(battleId).orElseThrow();
                    battle.getParticipants().forEach(p -> p.getDisconnections().size());
                    return battle;
                });
    }

    /** 배치고사 판을 요청하고 출발시킨다. */
    private Battle startedRound(long memberId, StandardDistance distance) {
        MatchAcceptance accepted = matching.requestMatch(memberId, distance);
        assertThat(accepted.waiting()).isFalse();
        Battle battle = load(accepted.battleId());
        battleClock.tick(battle.getStartAt().orElseThrow());
        return load(battle.getId());
    }

    /** 대결 기록 업로드(시작 = 대결 출발 시각). */
    private void upload(long memberId, Battle battle, long meters, int seconds) {
        LocalDateTime start = battle.getStartAt().orElseThrow();
        List<LocationPoint> points =
                RunFixtures.straightLine(start, (double) meters / seconds, seconds);
        uploads.upload(
                memberId,
                new RunUploadCommand(
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
                                List.of())));
    }

    private void freeRun(long memberId, LocalDateTime start, long meters, int seconds) {
        uploads.upload(
                memberId,
                new RunUploadCommand(
                        RunFixtures.upload(UUID.randomUUID().toString(), start, meters, seconds)));
    }

    /** 5km 25분(점수 약 38 → 골드)으로 한 판을 이긴다. */
    private Battle winRound(long memberId) {
        Battle battle = startedRound(memberId, StandardDistance.KM5);
        upload(memberId, battle, 5_000, 1_500);
        return load(battle.getId());
    }

    private PlacementRoundState stateOf(long memberId, Battle battle) {
        return roundReader.stateOf(memberId, battle.getId()).orElseThrow();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5911 언랭 요청은 대기열 없이 배치고사 1판(GHOST)을 만들고 진행 행이 생긴다. 상대 티어는 숨김, 응답에 판 번호·대결 ID")
    void t59_unrankedRequestCreatesPlacementBattle() {
        long m = member("새곰");

        MatchAcceptance accepted = matching.requestMatch(m, StandardDistance.KM3);

        assertThat(accepted.waiting()).isFalse();
        assertThat(accepted.placementRound()).isEqualTo(1);
        assertThat(accepted.distance()).isEqualTo(StandardDistance.KM3);
        assertThat(queue.findWaiting(m)).isEmpty();
        Battle battle = load(accepted.battleId());
        assertThat(battle.getType()).isEqualTo(BattleType.GHOST);
        assertThat(battle.placementRound()).hasValue(1);
        assertThat(battle.getOpponent().orElseThrow().displayTier()).isEqualTo(Tier.UNRANKED);
        assertThat(placements.view(m).status()).isEqualTo(PlacementStatus.IN_PROGRESS);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from placement_progress where member_id = ?",
                                Integer.class,
                                m))
                .isEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5912 같은 회원의 배치고사 요청 6개가 동시에 와도 대결은 하나, 나머지는 ALREADY_IN_BATTLE(D-478)")
    void concurrentPlacementRequests_onlyOneBattle() throws Exception {
        long m = member("동시곰");
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(
                    pool.submit(
                            () -> {
                                go.await();
                                try {
                                    return matching.requestMatch(m, StandardDistance.KM5);
                                } catch (BusinessRuleException e) {
                                    return e.reason();
                                }
                            }));
        }
        go.countDown();
        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> f : futures) {
            outcomes.add(f.get());
        }
        pool.shutdown();

        assertThat(outcomes).filteredOn(o -> o instanceof MatchAcceptance).hasSize(1);
        assertThat(outcomes)
                .filteredOn(o -> o instanceof ReasonCode)
                .hasSize(threads - 1)
                .allMatch(o -> o == ReasonCode.ALREADY_IN_BATTLE);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from battle_participant where member_id = ?",
                                Integer.class,
                                m))
                .isEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5913 배치고사 요청과 셀프런 시작이 동시에 오면 하나만 성공하고 나머지는 ALREADY_IN_BATTLE(활성 칸 고유 제약, D-515)")
    void placementVersusSelfRun_oneWins() throws Exception {
        long m = member("경합곰");
        freeRun(m, LocalDateTime.now().minusDays(2), 5_000, 1_800);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<Object> placement =
                pool.submit(
                        () -> {
                            go.await();
                            try {
                                return matching.requestMatch(m, StandardDistance.KM5);
                            } catch (BusinessRuleException e) {
                                return e.reason();
                            }
                        });
        Future<Object> self =
                pool.submit(
                        () -> {
                            go.await();
                            try {
                                return recordBattles.startSelfBattle(m, StandardDistance.KM5);
                            } catch (BusinessRuleException e) {
                                return e.reason();
                            }
                        });
        go.countDown();
        List<Object> outcomes = List.of(placement.get(), self.get());
        pool.shutdown();

        assertThat(outcomes).filteredOn(o -> o == ReasonCode.ALREADY_IN_BATTLE).hasSize(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from battle_participant where member_id = ? and"
                                        + " active_member_id is not null",
                                Integer.class,
                                m))
                .isEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5914 세 판을 뛰면 1·2판 임시 티어, 3판에 세 판 평균으로 확정(COMPLETED, 공개 티어 반영). 그 뒤 요청은 대기열")
    void t60_threeRoundsConfirmTier() {
        long m = member("세판곰");

        Battle first = winRound(m);
        assertThat(first.getStatus()).isEqualTo(BattleStatus.FINISHED);
        assertThat(stateOf(m, first)).isEqualTo(PlacementRoundState.COUNTED);
        PlacementView afterOne = placements.view(m);
        assertThat(afterOne.countedRounds()).isEqualTo(1);
        assertThat(afterOne.provisionalTier()).isEqualTo(Tier.GOLD);
        assertThat(afterOne.tier()).isEqualTo(Tier.UNRANKED);

        Battle second = winRound(m);
        assertThat(second.placementRound()).hasValue(2);
        winRound(m);

        PlacementView done = placements.view(m);
        assertThat(done.status()).isEqualTo(PlacementStatus.COMPLETED);
        assertThat(done.rounds()).hasSize(3);
        assertThat(done.tier()).isEqualTo(Tier.GOLD);
        assertThat(memberQuery.summaries(List.of(m)).get(m).tier()).isEqualTo(Tier.GOLD);
        assertThat(results.result(m, first.getId()).verdict()).isEqualTo(Verdict.WIN);

        MatchAcceptance next = matching.requestMatch(m, StandardDistance.KM5);
        assertThat(next.waiting()).isTrue();
        assertThat(next.placementRound()).isNull();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5915 진행 중 포기한 판은 세지 않는다(NOT_COUNTED, 0/3 그대로)")
    void forfeitDuringRound_isNotCounted() {
        long m = member("포기곰");
        Battle battle = startedRound(m, StandardDistance.KM1);

        results.forfeit(m, battle.getId());

        assertThat(load(battle.getId()).getStatus()).isEqualTo(BattleStatus.FINISHED);
        assertThat(stateOf(m, battle)).isEqualTo(PlacementRoundState.NOT_COUNTED);
        assertThat(placements.view(m).countedRounds()).isZero();
        assertThat(placements.view(m).nextRound()).isEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5916 무효 기록이 붙은 판은 세지 않는다(NOT_COUNTED)")
    void invalidRecord_isNotCounted() {
        long m = member("무효곰");
        Battle battle = startedRound(m, StandardDistance.KM1);

        upload(m, battle, 900, 300); // 1km 미만: 집계 제외(무효)

        assertThat(stateOf(m, load(battle.getId()))).isEqualTo(PlacementRoundState.NOT_COUNTED);
        assertThat(placements.view(m).countedRounds()).isZero();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5917 기록 없이 24시간 보류가 끝나 판정된 판은 세지 않는다")
    void missingRecordExpired_isNotCounted() {
        long m = member("무소식곰");
        Battle battle = startedRound(m, StandardDistance.KM1);
        LocalDateTime start = battle.getStartAt().orElseThrow();
        LocalDateTime overtime = start.plusMinutes(15);
        battleClock.tick(overtime);
        assertThat(load(battle.getId()).getStatus()).isEqualTo(BattleStatus.RESULT_PENDING);

        battleClock.tick(overtime.plus(Duration.ofHours(24)).plusSeconds(1));

        assertThat(load(battle.getId()).getStatus()).isEqualTo(BattleStatus.FINISHED);
        assertThat(stateOf(m, battle)).isEqualTo(PlacementRoundState.NOT_COUNTED);
        assertThat(placements.view(m).countedRounds()).isZero();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5918 출발 전에 그만두면 취소(CANCELLED)라 세지 않고, 다음 요청이 같은 1판을 다시 만든다")
    void cancelledBeforeStart_isNotCounted() {
        long m = member("취소곰");
        MatchAcceptance accepted = matching.requestMatch(m, StandardDistance.KM5);

        results.forfeit(m, accepted.battleId());

        assertThat(load(accepted.battleId()).getStatus()).isEqualTo(BattleStatus.CANCELLED);
        assertThatThrownBy(() -> roundReader.stateOf(m, accepted.battleId()))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.BATTLE_NOT_FOUND);
        assertThat(placements.view(m).countedRounds()).isZero();
        assertThat(matching.requestMatch(m, StandardDistance.KM5).placementRound()).isEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5919 유령보다 늦어 대결 거리에 못 닿은 유효 기록(4.5km)도 센다: 판정은 패, 판은 COUNTED(R82·D-511)")
    void lateValidRecordNotReachingDistance_isCounted() {
        long m = member("늦은곰");
        Battle battle = startedRound(m, StandardDistance.KM5);

        upload(m, battle, 4_500, 1_800);

        Battle judged = load(battle.getId());
        assertThat(judged.participant(m).orElseThrow().getVerified()).contains(false);
        assertThat(results.result(m, battle.getId()).verdict()).isEqualTo(Verdict.LOSE);
        assertThat(stateOf(m, judged)).isEqualTo(PlacementRoundState.COUNTED);
        assertThat(placements.view(m).countedRounds()).isEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5920 유령 먼저 도착 + 러너 카운트다운 안 도착 + 기록이 마감 뒤 업로드: 마감 판정(패) 뒤 기록 연결 사건으로 바로 센다(D-512)")
    void recordAfterJudgement_countsImmediately() {
        long m = member("마감곰");
        Battle battle = startedRound(m, StandardDistance.KM1);
        LocalDateTime start = battle.getStartAt().orElseThrow();
        Duration ghost = battle.getOpponent().orElseThrow().arrivalElapsed();
        LocalDateTime ghostAt = start.plus(ghost);
        battleClock.tick(ghostAt.plusNanos(1_000_000));
        assertThat(load(battle.getId()).getStatus()).isEqualTo(BattleStatus.COUNTDOWN);
        int mine = (int) ghost.plusSeconds(5).toSeconds();
        // 러너가 카운트다운 안(유령 + 5초)에 도착했다고 실시간 중계가 남긴 것과 같게 기록한다
        tx.executeWithoutResult(
                st -> {
                    Battle running = battles.findById(battle.getId()).orElseThrow();
                    running.recordArrival(Side.member(m), start.plusSeconds(mine));
                    battles.save(running);
                });
        battleClock.tick(ghostAt.plusSeconds(11));
        assertThat(load(battle.getId()).getStatus()).isEqualTo(BattleStatus.FINISHED);
        assertThat(placements.view(m).countedRounds()).as("기록 전에는 셀 수 없다").isZero();

        upload(m, battle, 1_000, mine);

        assertThat(placements.view(m).countedRounds()).isEqualTo(1);
        assertThat(stateOf(m, battle)).isEqualTo(PlacementRoundState.COUNTED);
    }

    /** 수신자가 실패한 것처럼 센 판을 지운다(판정·기록은 그대로, 진행 0/3). 동시 셈 시험의 출발점. */
    private void forgetCounts(long memberId) {
        jdbc.update("delete from placement_round where member_id = ?", memberId);
        jdbc.update(
                "update placement_progress set counted_rounds = 0, provisional_tier = null"
                        + " where member_id = ?",
                memberId);
    }

    private int placementRounds(long memberId) {
        return jdbc.queryForObject(
                "select count(*) from placement_round where member_id = ?",
                Integer.class,
                memberId);
    }

    private int countedRounds(long memberId) {
        return jdbc.queryForObject(
                "select counted_rounds from placement_progress where member_id = ?",
                Integer.class,
                memberId);
    }

    /** 작업들을 CountDownLatch로 같이 출발시키고 끝까지 기다린다. 작업의 예외는 그대로 던진다. */
    private static List<Object> together(List<Callable<Object>> jobs) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(jobs.size());
        CountDownLatch ready = new CountDownLatch(jobs.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<Object> job : jobs) {
            futures.add(
                    pool.submit(
                            () -> {
                                ready.countDown();
                                go.await();
                                return job.call();
                            }));
        }
        ready.await();
        go.countDown();
        List<Object> outcomes = new ArrayList<>();
        try {
            for (Future<Object> f : futures) {
                outcomes.add(f.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        return outcomes;
    }

    /** from 이후 출력에서 오류 로그(ERROR 수준 또는 셈 실패) 줄 수. */
    private static long errorLines(CapturedOutput output, int from) {
        return output.getAll()
                .substring(from)
                .lines()
                .filter(l -> l.contains(" ERROR ") || l.contains("placement_count_failed"))
                .count();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5923 판정 사건·기록 연결·복구 작업 세 계기가 같은 판을 동시에 불러도 판 행 1, countedRounds 1, 오류 로그 0(D-516,"
                    + " PL1-T1)")
    void threeTriggersSameRound_countOnce(CapturedOutput output) throws Exception {
        for (int i = 0; i < 3; i++) {
            long m = member("세번곰" + i);
            Battle battle = winRound(m);
            forgetCounts(m);
            int from = output.getAll().length();

            together(
                    List.of(
                            () -> roundListener.count(battle.getId()),
                            () -> roundListener.count(battle.getId()),
                            () -> reconcile.reconcile(LocalDateTime.now().plusHours(1))));

            assertThat(countedRounds(m)).as("반복 %d", i).isEqualTo(1);
            assertThat(placementRounds(m)).as("반복 %d", i).isEqualTo(1);
            assertThat(errorLines(output, from)).as("반복 %d", i).isZero();
        }
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5923 같은 회원의 다른 두 판을 동시에 세도 판 행 수 = countedRounds = 2, 오류 로그 0(PL1-B2·PL1-T1)")
    void twoRoundsSameMember_countedConcurrently_keepBoth(CapturedOutput output) throws Exception {
        for (int i = 0; i < 3; i++) {
            long m = member("두판곰" + i);
            Battle first = winRound(m);
            forgetCounts(m);
            Battle second = winRound(m);
            forgetCounts(m);
            int from = output.getAll().length();

            together(
                    List.of(
                            () -> roundListener.count(first.getId()),
                            () -> roundListener.count(second.getId())));

            assertThat(countedRounds(m)).as("반복 %d", i).isEqualTo(2);
            assertThat(placementRounds(m)).as("반복 %d", i).isEqualTo(2);
            assertThat(placements.view(m).rounds())
                    .extracting(r -> r.battleId())
                    .containsExactlyInAnyOrder(first.getId(), second.getId());
            assertThat(errorLines(output, from)).as("반복 %d", i).isZero();
        }
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "PL1-B1 기록 ID는 붙었는데 요약이 없는 판: 결과 조회가 실패하지 않고 24시간 전 PENDING, 뒤 NOT_COUNTED(TF-V18-01)")
    void summaryMissing_resultReadable_pendingThenNotCounted(CapturedOutput output) {
        long m = member("요약곰");
        Battle battle = startedRound(m, StandardDistance.KM1);
        LocalDateTime overtime = battle.getStartAt().orElseThrow().plusMinutes(15);
        battleClock.tick(overtime);
        battleClock.tick(overtime.plus(Duration.ofHours(24)).plusSeconds(1));
        assertThat(load(battle.getId()).getStatus()).isEqualTo(BattleStatus.FINISHED);
        // 기록 ID만 붙고 기록(요약)은 없는 판, 판정 23시간 뒤
        jdbc.update(
                "update battle_participant set record_id = ? where battle_id = ?",
                UUID.randomUUID().toString(),
                battle.getId());
        jdbc.update(
                "update battle set judged_at = ? where id = ?",
                LocalDateTime.now().minusHours(23),
                battle.getId());
        int from = output.getAll().length();

        // GET /battles/{id}/result가 부르는 두 읽기(BattleResultAssembler)가 모두 성공해야 200이다
        assertThat(results.result(m, battle.getId()).battleId()).isEqualTo(battle.getId());
        assertThat(stateOf(m, battle)).isEqualTo(PlacementRoundState.PENDING);
        assertThat(roundListener.count(battle.getId())).isFalse();
        assertThat(errorLines(output, from)).isZero();

        jdbc.update(
                "update battle set judged_at = ? where id = ?",
                LocalDateTime.now().minusHours(25),
                battle.getId());

        assertThat(results.result(m, battle.getId()).battleId()).isEqualTo(battle.getId());
        assertThat(stateOf(m, battle)).isEqualTo(PlacementRoundState.NOT_COUNTED);
        assertThat(placements.view(m).countedRounds()).isZero();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5926 배치고사가 아닌 러닝(자유 러닝 12회)은 판으로 세지 않고 티어도 정하지 않는다(J24-5·J24-6)")
    void otherRunsDoNotCountForPlacement() {
        long m = member("자유곰");
        for (int i = 0; i < 12; i++) {
            freeRun(m, LocalDateTime.now().minusDays(20).plusHours(i), 5_000, 1_500);
        }

        assertThat(memberQuery.summaries(List.of(m)).get(m).tier()).isEqualTo(Tier.UNRANKED);
        assertThat(placements.view(m).countedRounds()).isZero();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5927 탈퇴하면 진행·판 행이 지워지고 복구 작업도 되살리지 않는다. 다시 가입하면 0/3에서 시작(J24-9·D-489·D-516)")
    void withdrawal_deletesAndNothingRevivesRejoinStartsAtZero() {
        long m = member("떠날곰");
        Battle battle = winRound(m);
        assertThat(placements.view(m).countedRounds()).isEqualTo(1);

        withdrawals.withdraw(m);
        reconcile.reconcile(LocalDateTime.now().plusHours(1));
        roundListener.count(battle.getId());

        assertThat(jdbc.queryForObject("select count(*) from placement_progress", Integer.class))
                .isZero();
        long again = member("돌아온곰");
        assertThat(placements.view(again).countedRounds()).isZero();
        assertThat(placements.view(again).nextRound()).isEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5928 배포 전부터 티어가 있던 회원은 진행 행 없이 EXEMPT이고 요청은 지금처럼 대기열이다(J24-10·브리프 12)")
    void existingMembersAfterDeploy() {
        long m = member("옛곰");
        tx.executeWithoutResult(
                s -> {
                    Member member = members.findById(m).orElseThrow();
                    member.confirmPlacement(Tier.SILVER);
                    members.save(member);
                });

        assertThat(placements.view(m).status()).isEqualTo(PlacementStatus.EXEMPT);
        assertThat(matching.requestMatch(m, StandardDistance.KM5).waiting()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from placement_progress", Integer.class))
                .isZero();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5929 임시 티어는 본인 조회에만 있고 다른 사람이 보는 티어는 UNRANKED(배치 중)다(D-485)")
    void provisionalTierVisibleOnlyToSelf() {
        long m = member("임시곰");
        winRound(m);

        assertThat(placements.view(m).provisionalTier()).isEqualTo(Tier.GOLD);
        assertThat(memberQuery.summaries(List.of(m)).get(m).tier()).isEqualTo(Tier.UNRANKED);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5931 배치고사 성사까지 서버 처리는 3초 안(대기열·15초 고스트 전환 없음, NFR-005)")
    void placementMatch_p95WithinThreeSeconds() {
        List<Long> millis = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long m = member("빠른곰" + i);
            long begin = System.nanoTime();
            matching.requestMatch(m, StandardDistance.KM5);
            millis.add((System.nanoTime() - begin) / 1_000_000);
        }
        millis.sort(Long::compare);
        assertThat(millis.get((int) Math.ceil(millis.size() * 0.95) - 1)).isLessThan(3_000L);
    }
}
