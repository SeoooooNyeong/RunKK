package com.a207.runkk.battle;

import static com.a207.runkk.battle.BattleFixtures.ALICE;
import static com.a207.runkk.battle.BattleFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 판 인정 판단(05-21 D-511, R82): 붙은 기록의 RunSummary.valid만 본다. 판정용 검증값(유효 && 거리 도달)은 쓰지 않는다.
 * TF-V18-01(총괄 결정): 기록 ID는 붙었는데 요약을 읽지 못한 판은 판정 뒤 24시간 유예가 지나면 세지 않는다.
 */
@org.junit.jupiter.api.DisplayName("PlacementRoundCheck 판 인정(D-511)")
class PlacementRoundCheckTest {

    static final Duration GHOST = Duration.ofMinutes(30);
    static final LocalDateTime START = NOW.plusSeconds(13);

    /** 출발한 배치고사 1판(앨리스 vs 30분 유령, 5km). */
    static Battle started() {
        Battle battle = Battle.againstRecord(BattleFixtures.spec(BattleType.GHOST, GHOST), NOW);
        battle.markPlacement(1);
        battle.scheduleStart(START);
        battle.start(START);
        return battle;
    }

    /** 기록이 붙고 판정된 판. reached = 대결 거리 도달(판정 검증값), reach = 도달 경과 시간. */
    static Battle judged(boolean reached, Duration reach) {
        Battle battle = started();
        battle.verify(ALICE, new RecordCheck("r-1", reached, reach, 4));
        battle.judge(START.plus(Duration.ofMinutes(40)));
        return battle;
    }

    /** 기록 없이 24시간 보류가 끝나 판정된 판. */
    static Battle expiredWithoutRecord() {
        Battle battle = started();
        LocalDateTime overtime = START.plus(Duration.ofMinutes(75));
        battle.closeCountdown(overtime);
        battle.expirePending(overtime.plus(Battle.PENDING_LIMIT));
        return battle;
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5743 끝났고 유효 기록이 거리에 닿았으면 센다")
    void finishedValidReached_countable() {
        PlacementRoundCheck check =
                PlacementRoundCheck.of(judged(true, Duration.ofMinutes(25)), ALICE, true);
        assertThat(check.countable()).isTrue();
        assertThat(check.isVoid()).isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5744 유령보다 10초 넘게 늦어 대결 거리에 못 닿은 유효 기록도 센다(검증값 거짓이어도, R82)")
    void finishedValidButNotReached_countable() {
        Battle battle = judged(false, null);
        assertThat(battle.participant(ALICE).orElseThrow().getVerified()).contains(false);

        PlacementRoundCheck check = PlacementRoundCheck.of(battle, ALICE, true);

        assertThat(check.countable()).isTrue();
        assertThat(check.isVoid()).isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5745 본인이 포기한 판은 세지 않는다(void)")
    void forfeited_isVoid() {
        Battle battle = started();
        battle.forfeit(ALICE, START.plusMinutes(3));
        PlacementRoundCheck check = PlacementRoundCheck.of(battle, ALICE, null);
        assertThat(check.countable()).isFalse();
        assertThat(check.isVoid()).isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5746 출발 전 취소한 판은 void")
    void cancelled_isVoid() {
        Battle battle = Battle.againstRecord(BattleFixtures.spec(BattleType.GHOST, GHOST), NOW);
        battle.markPlacement(1);
        battle.cancelUnstarted(NOW.plusSeconds(5));
        PlacementRoundCheck check = PlacementRoundCheck.of(battle, ALICE, null);
        assertThat(check.cancelled()).isTrue();
        assertThat(check.isVoid()).isTrue();
        assertThat(check.countable()).isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5747 붙은 기록이 무효 러닝이면 void")
    void invalidRecord_isVoid() {
        PlacementRoundCheck check = PlacementRoundCheck.of(judged(false, null), ALICE, false);
        assertThat(check.isVoid()).isTrue();
        assertThat(check.countable()).isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5748 기록 없이 24시간 보류가 끝나 판정된 판은 void")
    void expiredWithoutRecord_isVoid() {
        Battle battle = expiredWithoutRecord();
        assertThat(battle.getStatus()).isEqualTo(BattleStatus.FINISHED);
        PlacementRoundCheck check = PlacementRoundCheck.of(battle, ALICE, null);
        assertThat(check.expiredWithoutRecord()).isTrue();
        assertThat(check.isVoid()).isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5749 아직 끝나지 않은 판(진행 중·보류)은 셀 수도 버릴 수도 없다")
    void notFinished_neitherCountableNorVoid() {
        Battle running = started();
        PlacementRoundCheck check = PlacementRoundCheck.of(running, ALICE, null);
        assertThat(check.countable()).isFalse();
        assertThat(check.isVoid()).isFalse();

        Battle pending = started();
        pending.closeCountdown(START.plus(Duration.ofMinutes(75)));
        assertThat(PlacementRoundCheck.of(pending, ALICE, null).isVoid()).isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5750 기록 ID는 붙었는데 요약을 읽지 못하면(유예 안) 셀 수도 버릴 수도 없다")
    void recordSummaryMissing_neitherCountableNorVoid() {
        Battle battle = judged(true, Duration.ofMinutes(25));
        PlacementRoundCheck check =
                PlacementRoundCheck.of(battle, ALICE, null, START.plus(Duration.ofHours(2)));
        assertThat(check.countable()).isFalse();
        assertThat(check.isVoid()).isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TF-V18-01 요약을 읽지 못한 채 판정 뒤 24시간 유예가 지나면 void(NOT_COUNTED로 닫힘, 총괄 결정)")
    void recordSummaryMissingAfterGrace_isVoid() {
        Battle battle = judged(true, Duration.ofMinutes(25));
        LocalDateTime judgedAt = battle.getJudgement().orElseThrow().at();

        assertThat(
                        PlacementRoundCheck.of(
                                        battle,
                                        ALICE,
                                        null,
                                        judgedAt.plus(Battle.PENDING_LIMIT).minusSeconds(1))
                                .isVoid())
                .isFalse();
        PlacementRoundCheck after =
                PlacementRoundCheck.of(battle, ALICE, null, judgedAt.plus(Battle.PENDING_LIMIT));
        assertThat(after.summaryMissingExpired()).isTrue();
        assertThat(after.isVoid()).isTrue();
        assertThat(PlacementRoundState.of(after, false, false))
                .isEqualTo(PlacementRoundState.NOT_COUNTED);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5751 24시간 만료 판정 뒤 늦게 붙은 유효 기록도 붙는 때 센다")
    void lateValidRecordAfterExpiry_countable() {
        Battle battle = expiredWithoutRecord();
        battle.verify(ALICE, new RecordCheck("late", true, Duration.ofMinutes(31), 5));
        battle.judge(START.plus(Battle.PENDING_LIMIT).plusHours(2));

        PlacementRoundCheck check = PlacementRoundCheck.of(battle, ALICE, true);

        assertThat(check.expiredWithoutRecord()).isFalse();
        assertThat(check.countable()).isTrue();
    }

    @ParameterizedTest(
            name =
                    "TC-5811 countable MC/DC: finished={0} forfeited={1} hasRecord={2} valid={3} →"
                            + " {4}")
    @CsvSource({
        "true, false, true, true, true",
        "false, false, true, true, false",
        "true, true, true, true, false",
        "true, false, false, , false",
        "true, false, true, false, false",
        "true, false, true, , false"
    })
    void countable_mcdc(
            boolean finished,
            boolean forfeited,
            boolean hasRecord,
            Boolean valid,
            boolean expected) {
        PlacementRoundCheck check =
                new PlacementRoundCheck(finished, false, forfeited, hasRecord, valid, false, false);
        assertThat(check.countable()).isEqualTo(expected);
    }

    @ParameterizedTest(
            name =
                    "TC-5812 isVoid MC/DC: cancelled={0} forfeited={1} valid={2} expired={3}"
                            + " missing={4} → {5}")
    @CsvSource({
        "false, false, true, false, false, false",
        "true, false, true, false, false, true",
        "false, true, true, false, false, true",
        "false, false, false, false, false, true",
        "false, false, , true, false, true",
        "false, false, , false, true, true",
        "false, false, , false, false, false"
    })
    void isVoid_mcdc(
            boolean cancelled,
            boolean forfeited,
            Boolean valid,
            boolean expired,
            boolean missing,
            boolean expected) {
        PlacementRoundCheck check =
                new PlacementRoundCheck(true, cancelled, forfeited, true, valid, expired, missing);
        assertThat(check.isVoid()).isEqualTo(expected);
    }
}
