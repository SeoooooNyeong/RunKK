package com.a207.runkk.battle;

import static com.a207.runkk.battle.BattleFixtures.ALICE;
import static com.a207.runkk.battle.PlacementRoundCheckTest.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.Distance;
import com.a207.runkk.member.InMemoryMemberRepository;
import com.a207.runkk.member.InMemoryPlacementProgressRepository;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.PlacementProgress;
import com.a207.runkk.member.PlacementService;
import com.a207.runkk.policy.InMemoryOperationSettingRepository;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.running.CardKind;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunSummary;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 판 셈 누락 복구(05-21 D-490): 최근 7일 끝난 배치고사 대결을 다시 센다. 탈퇴로 지워진 진행은 되살리지 않는다. */
@org.junit.jupiter.api.DisplayName("PlacementReconcileService 판 셈 누락 복구(D-490)")
class PlacementReconcileServiceTest {

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
    private final PlacementReconcileService service =
            new PlacementReconcileService(
                    battles,
                    new PlacementRoundListener(
                            battles,
                            runs,
                            placementService,
                            InMemoryBattleStores.noTransactionManager()));

    @BeforeEach
    void wire() {
        members.save(MemberFixtures.member("배치곰"));
        when(runs.summary(anyString()))
                .thenReturn(
                        new RunSummary(
                                "r-1",
                                ALICE,
                                START,
                                Distance.ofKm(5),
                                Duration.ofMinutes(25),
                                null,
                                true,
                                38.0,
                                RunMode.BATTLE,
                                CardKind.BATTLE_RUN,
                                1L,
                                null));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5793 판정 사건을 놓친 판을 다시 센다(최근 7일 안만). 다시 돌려도 한 번만 센다")
    void reconcile_recountsWithinSevenDays() {
        progress.put(PlacementProgress.start(ALICE));
        Battle missed = battles.save(PlacementRoundCheckTest.judged(true, Duration.ofMinutes(25)));
        LocalDateTime judgedAt = missed.getJudgement().orElseThrow().at();

        assertThat(service.reconcile(judgedAt.plusDays(8))).as("7일 밖").isZero();
        assertThat(placementService.view(ALICE).countedRounds()).isZero();

        assertThat(service.reconcile(judgedAt.plusDays(1))).isEqualTo(1);
        assertThat(placementService.view(ALICE).countedRounds()).isEqualTo(1);
        service.reconcile(judgedAt.plusDays(1));
        assertThat(placementService.view(ALICE).countedRounds()).as("멱등").isEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5794 탈퇴로 진행 행이 지워진 회원의 판은 세지 않고 행을 만들지 않는다(J24-9, D-516)")
    void reconcile_withdrawnMember_createsNothing() {
        Battle missed = battles.save(PlacementRoundCheckTest.judged(true, Duration.ofMinutes(25)));
        members.findById(ALICE).orElseThrow().withdraw(START);

        service.reconcile(missed.getJudgement().orElseThrow().at().plusHours(1));

        assertThat(progress.has(ALICE)).isFalse();
    }
}
