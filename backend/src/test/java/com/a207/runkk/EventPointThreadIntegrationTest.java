package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.battle.BattleHistoryService;
import com.a207.runkk.battle.BattleJudged;
import com.a207.runkk.battle.BattleType;
import com.a207.runkk.battle.CelebrityChallenge;
import com.a207.runkk.battle.CelebrityWin;
import com.a207.runkk.battle.Judgement;
import com.a207.runkk.battle.Side;
import com.a207.runkk.battle.Verdict;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.event.CoachWinPointListener;
import com.a207.runkk.event.EventInput;
import com.a207.runkk.event.EventKind;
import com.a207.runkk.event.EventService;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.reward.AccrualReason;
import com.a207.runkk.reward.RewardAccrualRepository;
import com.a207.runkk.reward.RewardService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 이벤트 응모와 코치 승리 포인트를 실 PostgreSQL에서 확인한다(S15P31A207-275, test/이벤트-성 9절 보강 1~3). 하루 적립 합계 sumOn이 이벤트
 * 포인트를 빼는지(TC-2642), battle 셀럽 첫 승리 질의가 진 판, 포기, 보류, 다른 종류를 빼고 판정일시 양 끝을 넣는지(TC-2628~TC-2630), 같은
 * 회원의 셀럽런 판정이 동시에 커밋돼도 500P와 지급 이력이 한 번인지(TC-2651).
 */
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("이벤트 응모 질의와 코치 승리 포인트 동시성(PostgreSQL)")
class EventPointThreadIntegrationTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 8, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 16, 23, 59, 59);
    private static final AtomicInteger NAMES = new AtomicInteger();

    @Autowired RewardAccrualRepository accruals;
    @Autowired RewardService rewards;
    @Autowired BattleHistoryService battles;
    @Autowired EventService events;
    @Autowired CoachWinPointListener listener;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private long member() {
        String name = "이벤트러너" + NAMES.incrementAndGet();
        return tx.execute(
                s ->
                        members.save(MemberFixtures.member(name, LocalDateTime.now().minusDays(30)))
                                .getId());
    }

    /** 다른 시험의 셀럽 ID와 겹치지 않는 값. */
    private static long celebrityBase() {
        return 700_000 + ThreadLocalRandom.current().nextLong(100_000) * 10;
    }

    private void accrual(long memberId, AccrualReason reason, int amount, LocalDateTime at) {
        jdbc.update(
                "insert into reward_accrual (member_id, reason, source_id, amount, accrued_at)"
                        + " values (?, ?, ?, ?, ?)",
                memberId,
                reason.name(),
                UUID.randomUUID().toString().substring(0, 30),
                amount,
                Timestamp.valueOf(at));
    }

    /** 대결 한 판. 셀럽 대결이면 상대 종류 CELEBRITY, 그 밖은 RUN_RECORD. 판정 전이면 judgedAt null. */
    private void battle(
            String type,
            String status,
            long celebrityId,
            LocalDateTime judgedAt,
            Long winnerMember) {
        LocalDateTime created = (judgedAt == null ? START.plusDays(1) : judgedAt).minusMinutes(30);
        jdbc.update(
                "insert into battle (type, distance_meters, status, created_at, opponent_kind,"
                        + " opponent_record_id, opponent_name, opponent_tier,"
                        + " opponent_arrival_millis, judged_at, corrected, winner_member_id,"
                        + " winner_opponent, version) values (?, 5000, ?, ?, ?, ?, '코치',"
                        + " 'UNRANKED', 1800000, ?, false, ?, ?, 0)",
                type,
                status,
                Timestamp.valueOf(created),
                "CELEBRITY".equals(type) ? "CELEBRITY" : "RUN_RECORD",
                String.valueOf(celebrityId),
                judgedAt == null ? null : Timestamp.valueOf(judgedAt),
                winnerMember,
                judgedAt != null && winnerMember == null);
    }

    private void celebrityWin(long memberId, long celebrityId, LocalDateTime judgedAt) {
        battle("CELEBRITY", "FINISHED", celebrityId, judgedAt, memberId);
    }

    @Test
    @DisplayName("sumOn: 같은 날 EVENT 500P는 하루 적립 합계에 들지 않고 다른 사유만 더한다(TC-2642)")
    void sumOn_excludesEventPoints() {
        long m = member();
        LocalDateTime day = LocalDateTime.of(2026, 11, 9, 9, 0);
        assertThat(rewards.accrueEvent(m, "1:celebrity-11", 500, day)).isEqualTo(500);
        accrual(m, AccrualReason.BATTLE_WIN, 20, day.plusHours(1));
        accrual(m, AccrualReason.VALID_RUN, 10, day.plusHours(2));
        accrual(m, AccrualReason.VALID_RUN, 15, day.plusDays(1));

        assertThat(accruals.sumOn(m, day.toLocalDate())).isEqualTo(30);
        assertThat(accruals.sumOn(m, day.toLocalDate().plusDays(1))).isEqualTo(15);
        assertThat(rewards.balanceOf(m)).isEqualTo(500);
    }

    @Test
    @DisplayName(
            "셀럽 첫 승리 질의: 진 판, 포기, 보류, 셀프런과 다른 종류 승리는 빼고 판정일시 양 끝은 넣으며 같은 셀럽 여러 승리는 가장 이른"
                    + " 것(TC-2628~TC-2630)")
    void celebrityWinQueries_filterAndBoundaries() {
        long m = 9_100_000 + ThreadLocalRandom.current().nextLong(100_000);
        long other = m + 1;
        long c = celebrityBase();
        long beforeStart = c;
        long atStart = c + 1;
        long atEnd = c + 2;
        long afterEnd = c + 3;
        long twice = c + 4;
        long lostOnly = c + 5;
        long selfRun = c + 6;
        long ghost = c + 7;
        LocalDateTime mid = LocalDateTime.of(2026, 11, 10, 19, 0);

        celebrityWin(m, beforeStart, START.minusSeconds(1));
        celebrityWin(m, atStart, START);
        celebrityWin(m, atStart, START.plusSeconds(1));
        celebrityWin(m, atEnd, END.minusSeconds(1));
        celebrityWin(m, atEnd, END);
        celebrityWin(m, afterEnd, END.plusSeconds(1));
        celebrityWin(m, twice, mid);
        celebrityWin(m, twice, mid.minusMinutes(1));
        // 진 판과 포기(포기자 패, 상대 승: 같은 행 모양), 판정 보류
        battle("CELEBRITY", "FINISHED", lostOnly, mid, null);
        battle("CELEBRITY", "FINISHED", lostOnly, mid.plusHours(1), null);
        battle("CELEBRITY", "RESULT_PENDING", lostOnly, null, null);
        // 셀프런, 고스트 승리는 상대 기록 ID가 같아도 셀럽 승리가 아니다
        battle("SELF", "FINISHED", selfRun, mid, m);
        battle("GHOST", "FINISHED", ghost, mid, m);
        celebrityWin(other, twice, mid.plusHours(2));

        List<Long> ids =
                List.of(beforeStart, atStart, atEnd, afterEnd, twice, lostOnly, selfRun, ghost);
        Map<Long, LocalDateTime> first = new java.util.HashMap<>();
        for (CelebrityChallenge challenge : battles.celebrityChallenges(m, ids, START, END)) {
            if (challenge.firstWonAt() != null) {
                first.put(challenge.celebrityId(), challenge.firstWonAt());
            }
        }

        assertThat(first)
                .containsOnly(
                        Map.entry(atStart, START),
                        Map.entry(atEnd, END.minusSeconds(1)),
                        Map.entry(twice, mid.minusMinutes(1)));
        assertThat(battles.celebrityWinners(ids, START, END))
                .containsExactly(
                        new CelebrityWin(m, atStart, START),
                        new CelebrityWin(m, atEnd, END.minusSeconds(1)),
                        new CelebrityWin(m, twice, mid.minusMinutes(1)),
                        new CelebrityWin(other, twice, mid.plusHours(2)));

        // 기간이 종료 정각 한 점이어도 그 시각 승리를 센다(양 끝 포함). 판정일시는 초 단위다
        assertThat(battles.celebrityChallenges(m, List.of(atEnd), END, END))
                .extracting(CelebrityChallenge::firstWonAt)
                .containsExactly(END);
    }

    @Test
    @DisplayName("같은 회원의 셀럽런 판정 네 건이 동시에 커밋돼도 500P 적립과 지급 이력은 한 번이고 실패 로그가 없다(TC-2651)")
    void concurrentJudgements_grantOnce(CapturedOutput output) throws Exception {
        long m = member();
        long celebrity = celebrityBase();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        long eventId =
                events.create(
                                "event-it",
                                new EventInput(
                                        "coach-it-" + UUID.randomUUID().toString().substring(0, 8),
                                        "코치님을 이겨라",
                                        "부제",
                                        EventKind.COACH_CELEB,
                                        "{\"celebrityIds\":["
                                                + celebrity
                                                + "],\"pointPerWin\":500}",
                                        now.minusDays(1),
                                        now.plusDays(1),
                                        "/events/it/banner.png",
                                        "/events/it/thumb.png",
                                        "/events/it/",
                                        1,
                                        true))
                        .id();
        LocalDateTime judgedAt = now.minusMinutes(5);
        celebrityWin(m, celebrity, judgedAt);
        celebrityWin(m, celebrity, judgedAt.plusMinutes(1));
        BattleJudged judged =
                new BattleJudged(
                        1L,
                        BattleType.CELEBRITY,
                        new Judgement(
                                Map.of(Side.member(m), Verdict.WIN, Side.OPPONENT, Verdict.LOSE),
                                Side.member(m),
                                false,
                                judgedAt),
                        judgedAt);

        int threads = 4;
        CyclicBarrier together = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> runs = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                runs.add(
                        pool.submit(
                                () -> {
                                    together.await(10, TimeUnit.SECONDS);
                                    listener.onBattleJudged(judged);
                                    return null;
                                }));
            }
            for (Future<?> run : runs) {
                run.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        String source = eventId + ":celebrity-" + celebrity;
        assertThat(rewards.accruedFor(AccrualReason.EVENT, source, m)).isEqualTo(500);
        assertThat(rewards.balanceOf(m)).isEqualTo(500);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from reward_accrual where member_id = ?"
                                        + " and reason = 'EVENT'",
                                Integer.class,
                                m))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from event_point_grant where member_id = ?"
                                        + " and event_id = ?",
                                Integer.class,
                                m,
                                eventId))
                .isEqualTo(1);
        // 늦게 온 판정이 이력 고유 제약에 걸려도 실패 로그(event_point_failed)를 남기지 않는다
        assertThat(output.getAll()).doesNotContain("event_point_failed");
    }
}
