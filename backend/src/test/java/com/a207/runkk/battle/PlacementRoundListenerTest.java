package com.a207.runkk.battle;

import static com.a207.runkk.battle.BattleFixtures.ALICE;
import static com.a207.runkk.battle.PlacementRoundCheckTest.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.InMemoryMemberRepository;
import com.a207.runkk.member.InMemoryPlacementProgressRepository;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.PlacementProgress;
import com.a207.runkk.member.PlacementRound;
import com.a207.runkk.member.PlacementService;
import com.a207.runkk.member.PlacementVerdict;
import com.a207.runkk.policy.InMemoryOperationSettingRepository;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.running.CardKind;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunSummary;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

/** PlacementRoundListener(05-21 D-481·D-511·D-512·D-516): 판정·기록 연결 사건을 받아 판을 센다. */
@org.junit.jupiter.api.DisplayName("PlacementRoundListener 판 셈(D-481)")
class PlacementRoundListenerTest {

    private final InMemoryMemberRepository members = new InMemoryMemberRepository();
    private final InMemoryPlacementProgressRepository progress =
            new InMemoryPlacementProgressRepository();
    private final PlacementService placementService =
            new PlacementService(
                    members,
                    progress,
                    new OperationPolicy(new InMemoryOperationSettingRepository()));
    private final InMemoryBattleStores.Battles battles = new InMemoryBattleStores.Battles();
    private final RunQueryService runs = mock(RunQueryService.class);
    private PlacementRoundListener listener;

    @BeforeEach
    void wire() {
        long id = members.save(MemberFixtures.member("배치곰")).getId();
        assertThat(id).isEqualTo(ALICE);
        progress.put(PlacementProgress.start(ALICE));
        listener = listenerWith(placementService);
    }

    private PlacementRoundListener listenerWith(PlacementService service) {
        return new PlacementRoundListener(
                battles, runs, service, InMemoryBattleStores.noTransactionManager());
    }

    private void summary(String recordId, boolean valid, double score) {
        when(runs.summary(recordId))
                .thenReturn(
                        new RunSummary(
                                recordId,
                                ALICE,
                                START,
                                Distance.ofMeters(4_620),
                                Duration.ofMinutes(32),
                                new Pace(415),
                                valid,
                                valid ? score : null,
                                RunMode.BATTLE,
                                CardKind.BATTLE_RUN,
                                1L,
                                null));
    }

    private BattleJudged judgedEvent(Battle battle) {
        Judgement j = battle.getJudgement().orElseThrow();
        return new BattleJudged(battle.getId(), battle.getType(), j, j.at());
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5785 판정되면 기록 요약 값(점수·거리·시간·페이스)과 승패·유효 순번으로 1판을 센다. 대결 거리 미도착이어도 유효면 센다")
    void judged_countsWithSummaryValues() {
        Battle battle = battles.save(PlacementRoundCheckTest.judged(false, null));
        summary("r-1", true, 26.4);

        listener.onBattleJudged(judgedEvent(battle));

        PlacementRound round = placementService.view(ALICE).rounds().get(0);
        assertThat(round.battleId()).isEqualTo(battle.getId());
        assertThat(round.score()).isEqualTo(26.4);
        assertThat(round.runDistance()).isEqualTo(Distance.ofMeters(4_620));
        assertThat(round.runElapsed()).isEqualTo(Duration.ofMinutes(32));
        assertThat(round.averagePace()).contains(new Pace(415));
        assertThat(round.verdict()).isEqualTo(PlacementVerdict.LOSE);
        assertThat(round.validRunOrdinal()).isEqualTo(4);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5786 배치고사가 아닌 대결(placementRound 없음)·고스트가 아닌 대결은 건너뛴다")
    void nonPlacementBattle_isSkipped() {
        Battle plain =
                Battle.againstRecord(
                        BattleFixtures.spec(BattleType.GHOST, Duration.ofMinutes(30)),
                        BattleFixtures.NOW);
        plain.scheduleStart(START);
        plain.start(START);
        plain.verify(ALICE, new RecordCheck("r-1", true, Duration.ofMinutes(25), 2));
        plain.judge(START.plusMinutes(40));
        battles.save(plain);
        summary("r-1", true, 40);

        assertThat(listener.count(plain.getId())).isFalse();
        listener.onBattleJudged(judgedEvent(plain));
        assertThat(placementService.view(ALICE).countedRounds()).isZero();

        Battle random = battles.save(BattleFixtures.startedRandom());
        PlacementService spy = mock(PlacementService.class);
        listenerWith(spy)
                .onBattleJudged(
                        new BattleJudged(
                                random.getId(),
                                BattleType.RANDOM,
                                new Judgement(Map.of(), null, false, START),
                                START));
        verify(spy, never()).countRound(anyLong(), any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5787 포기로 판정된 판은 세지 않는다(FR-094)")
    void forfeitJudged_isNotCounted() {
        Battle battle = PlacementRoundCheckTest.started();
        battle.forfeit(ALICE, START.plusMinutes(2));
        battles.save(battle);

        listener.onBattleJudged(judgedEvent(battle));

        assertThat(placementService.view(ALICE).countedRounds()).isZero();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5788 일시 오류는 3번까지 다시 하고 그래도 실패하면 거짓(운영 로그, 복구 작업이 다시 센다)")
    void transientFailure_retriesThreeTimesThenLogs() {
        Battle battle = battles.save(PlacementRoundCheckTest.judged(true, Duration.ofMinutes(25)));
        summary("r-1", true, 38);
        PlacementService flaky = mock(PlacementService.class);
        when(flaky.countRound(anyLong(), any())).thenThrow(new CannotAcquireLockException("lock"));

        assertThat(listenerWith(flaky).count(battle.getId())).isFalse();

        verify(flaky, times(3)).countRound(anyLong(), any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5768 uq_placement_round_battle 위반은 이미 센 것으로 보고 다시 하지 않는다(D-516, 리스너가 처리)")
    void uniqueViolation_isTreatedAsCounted() {
        Battle battle = battles.save(PlacementRoundCheckTest.judged(true, Duration.ofMinutes(25)));
        summary("r-1", true, 38);
        PlacementService racing = mock(PlacementService.class);
        when(racing.countRound(anyLong(), any()))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "dup", new SQLException("dup", "23505")));

        assertThat(listenerWith(racing).count(battle.getId())).isTrue();

        verify(racing, times(1)).countRound(anyLong(), any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5789 판정 없이 기록만 붙었다는 사건(PlacementRecordAttached)으로도 센다. 판정 전이면 아무것도 하지 않는다(D-512)")
    void recordAttached_counts() {
        Battle running = battles.save(PlacementRoundCheckTest.started());
        listener.onRecordAttached(new PlacementRecordAttached(running.getId(), ALICE, START));
        assertThat(placementService.view(ALICE).countedRounds()).isZero();

        Battle battle = battles.save(PlacementRoundCheckTest.judged(true, Duration.ofMinutes(25)));
        summary("r-1", true, 38);
        listener.onRecordAttached(new PlacementRecordAttached(battle.getId(), ALICE, START));

        assertThat(placementService.view(ALICE).countedRounds()).isEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("무효 기록·요약을 읽지 못한 기록은 세지 않는다")
    void invalidOrMissingSummary_notCounted() {
        Battle invalid = battles.save(PlacementRoundCheckTest.judged(false, null));
        summary("r-1", false, 0);
        assertThat(listener.count(invalid.getId())).isFalse();

        when(runs.summary("r-1")).thenThrow(new BusinessRuleException(ReasonCode.RECORD_NOT_FOUND));
        assertThat(listener.count(invalid.getId())).isFalse();
        assertThat(placementService.view(ALICE).countedRounds()).isZero();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("정정된 판정은 이미 센 판의 승패만 고친다(셈 여부는 그대로)")
    void correctedJudgement_updatesVerdictOnly() {
        Battle battle = battles.save(PlacementRoundCheckTest.judged(true, Duration.ofMinutes(25)));
        summary("r-1", true, 38);
        listener.onBattleJudged(judgedEvent(battle));
        assertThat(placementService.view(ALICE).rounds().get(0).verdict())
                .isEqualTo(PlacementVerdict.WIN);

        battle.verify(ALICE, new RecordCheck("r-1", true, Duration.ofMinutes(31), 4));
        Judgement corrected = battle.judge(START.plusHours(1)).orElseThrow();
        assertThat(corrected.corrected()).isTrue();
        listener.onBattleJudged(
                new BattleJudged(battle.getId(), BattleType.GHOST, corrected, corrected.at()));

        assertThat(placementService.view(ALICE).countedRounds()).isEqualTo(1);
        assertThat(placementService.view(ALICE).rounds().get(0).verdict())
                .isEqualTo(PlacementVerdict.LOSE);
    }
}
