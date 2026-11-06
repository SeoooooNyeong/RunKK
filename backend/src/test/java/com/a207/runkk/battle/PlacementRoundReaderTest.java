package com.a207.runkk.battle;

import static com.a207.runkk.battle.BattleFixtures.ALICE;
import static com.a207.runkk.battle.BattleFixtures.BOB;
import static com.a207.runkk.battle.PlacementRoundCheckTest.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
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
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** PlacementRoundReader(05-21 D-513): 판 결과 상태를 battle 안에서 계산한다. */
@org.junit.jupiter.api.DisplayName("PlacementRoundReader 판 결과 상태(D-513)")
class PlacementRoundReaderTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

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

    private PlacementRoundReader readerAt(LocalDateTime now) {
        return new PlacementRoundReader(
                battles, runs, placementService, Clock.fixed(now.atZone(SEOUL).toInstant(), SEOUL));
    }

    private void validSummary() {
        when(runs.findSummary("r-1"))
                .thenReturn(
                        Optional.of(
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
                                        null)));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5795 참가자가 아니면 기존 결과 조회와 같은 오류, 배치고사가 아니면 비어 있음, 셌으면 COUNTED, 셈 전이면 PENDING")
    void stateOf_participantNonPlacementCounted() {
        members.save(MemberFixtures.member("배치곰"));
        progress.put(PlacementProgress.start(ALICE));
        PlacementRoundReader reader = readerAt(START.plusHours(1));
        Battle placement =
                battles.save(PlacementRoundCheckTest.judged(true, Duration.ofMinutes(25)));
        validSummary();

        assertThatThrownBy(() -> reader.stateOf(BOB, placement.getId()))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> reader.stateOf(ALICE, 999L))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.BATTLE_NOT_FOUND);

        Battle plain = battles.save(BattleFixtures.startedRandom());
        assertThat(reader.stateOf(ALICE, plain.getId())).isEmpty();

        assertThat(reader.stateOf(ALICE, placement.getId())).contains(PlacementRoundState.PENDING);
        new PlacementRoundListener(
                        battles,
                        runs,
                        placementService,
                        InMemoryBattleStores.noTransactionManager())
                .count(placement.getId());
        assertThat(reader.stateOf(ALICE, placement.getId())).contains(PlacementRoundState.COUNTED);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TF-V18-01 요약을 읽지 못하는 판은 판정 뒤 24시간까지 PENDING, 그 뒤 NOT_COUNTED")
    void unreadableSummary_closesAfterGrace() {
        members.save(MemberFixtures.member("배치곰"));
        Battle placement =
                battles.save(PlacementRoundCheckTest.judged(true, Duration.ofMinutes(25)));
        when(runs.findSummary("r-1")).thenReturn(Optional.empty());
        LocalDateTime judgedAt = placement.getJudgement().orElseThrow().at();

        assertThat(readerAt(judgedAt.plusHours(23)).stateOf(ALICE, placement.getId()))
                .contains(PlacementRoundState.PENDING);
        assertThat(readerAt(judgedAt.plusHours(24)).stateOf(ALICE, placement.getId()))
                .contains(PlacementRoundState.NOT_COUNTED);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "PL1-B8 셀 수 있는데 세지 못한 판은 복구 창(판정 뒤 7일)까지 PENDING, 그 뒤 NOT_COUNTED(더 셀 계기가 없음)")
    void countableButUncounted_closesAfterRecoveryWindow() {
        members.save(MemberFixtures.member("배치곰"));
        progress.put(PlacementProgress.start(ALICE));
        Battle placement =
                battles.save(PlacementRoundCheckTest.judged(true, Duration.ofMinutes(25)));
        validSummary();
        LocalDateTime judgedAt = placement.getJudgement().orElseThrow().at();

        assertThat(readerAt(judgedAt.plusDays(7).minusSeconds(1)).stateOf(ALICE, placement.getId()))
                .contains(PlacementRoundState.PENDING);
        assertThat(readerAt(judgedAt.plusDays(7)).stateOf(ALICE, placement.getId()))
                .contains(PlacementRoundState.NOT_COUNTED);
    }
}
