package com.a207.runkk.battle;

import static com.a207.runkk.battle.BattleFixtures.ALICE;
import static com.a207.runkk.battle.BattleFixtures.BOB;
import static com.a207.runkk.battle.BattleFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.running.BestEntry;
import com.a207.runkk.running.CurvePoint;
import com.a207.runkk.running.RunComparison;
import com.a207.runkk.running.RunCurve;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 활성 대결 1개 불변식과 버려진 대결 정리: 시작 보고 없이 10분 지난 기록 대결·출발 시각이 10분 지나도 시작하지 못한 대결은 CANCELLED, 새 기록 대결은 시작
 * 보고 전 이전 기록 대결을 대신하고 출발한 대결이 있으면 거부한다.
 */
@DisplayName("버려진 대결 정리와 활성 대결 1개")
class AbandonedBattleTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Distance FIVE_K = StandardDistance.KM5.distance();

    private final InMemoryBattleStores.Battles battles = new InMemoryBattleStores.Battles();
    private final InMemoryBattleStores.Queue queue = new InMemoryBattleStores.Queue();
    private final MemberQueryService members = mock(MemberQueryService.class);
    private final RunQueryService runs = mock(RunQueryService.class);

    @BeforeEach
    void stubs() {
        when(members.hasConsent(anyLong(), eq(ConsentItem.LOCATION))).thenReturn(true);
        when(members.summaries(anyCollection()))
                .thenReturn(Map.of(ALICE, BattleFixtures.summary(ALICE, Tier.GOLD)));
        when(runs.best(ALICE, StandardDistance.KM5))
                .thenReturn(
                        Optional.of(
                                new BestEntry(
                                        "best",
                                        Duration.ofMinutes(25).toMillis(),
                                        null,
                                        LocalDate.of(2026, 10, 3),
                                        false)));
        when(runs.comparisonOf("best"))
                .thenReturn(
                        new RunComparison(Duration.ofMinutes(25), new Pace(300), new Pace(290)));
        when(runs.curve(eq("best"), any()))
                .thenReturn(
                        new RunCurve(
                                List.of(
                                        new CurvePoint(Duration.ZERO, Distance.ZERO),
                                        new CurvePoint(Duration.ofMinutes(25), FIVE_K)),
                                FIVE_K));
    }

    private static Clock at(LocalDateTime t) {
        return Clock.fixed(t.atZone(SEOUL).toInstant(), SEOUL);
    }

    private RecordBattleService records(LocalDateTime t) {
        return new RecordBattleService(
                members, runs, new InMemoryBattleStores.Celebrities(), battles, queue, at(t));
    }

    private BattleStartService starts(LocalDateTime t) {
        return new BattleStartService(
                battles,
                members,
                runs,
                new InMemoryBattleStores.Messenger(),
                new InMemoryBattleStores.Sessions(),
                at(t),
                InMemoryBattleStores.transactions());
    }

    private Battle selfBattle(LocalDateTime t) {
        long id = records(t).startSelfBattle(ALICE, StandardDistance.KM5).battleId();
        return battles.findById(id).orElseThrow();
    }

    @Test
    @DisplayName(
            "시작 보고 없는 기록 대결은 생성 10분 뒤 CANCELLED: 활성 아님, 판정·이력·결과 없음, 늦은 시작 보고는 BATTLE_NOT_FOUND")
    void unreportedRecordBattle_isCancelledAfterTenMinutes() {
        Battle battle = selfBattle(NOW);
        LocalDateTime limit = NOW.plus(Battle.ABANDON_AFTER);

        assertThat(starts(limit.minusSeconds(1)).cancelAbandoned(limit.minusSeconds(1))).isZero();
        assertThat(battle.isActive()).isTrue();
        assertThat(starts(limit).cancelAbandoned(limit)).isEqualTo(1);

        assertThat(battle.getStatus()).isEqualTo(BattleStatus.CANCELLED);
        assertThat(battle.isActive()).isFalse();
        assertThat(battles.existsActive(ALICE)).isFalse();
        assertThat(battle.judge(limit)).isEmpty();
        assertThat(battles.findHistory(ALICE, new DateRange(NOW.toLocalDate(), NOW.toLocalDate())))
                .isEmpty();
        assertThatThrownBy(() -> records(limit).markStarted(ALICE, battle.getId()))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.BATTLE_NOT_FOUND);
        assertThat(starts(limit).cancelAbandoned(limit.plusMinutes(1))).isZero();
    }

    @Test
    @DisplayName("시작 보고를 받은 기록 대결은 10분이 지나도 취소하지 않는다")
    void reportedRecordBattle_isKept() {
        Battle battle = selfBattle(NOW);
        records(NOW.plusSeconds(4)).markStarted(ALICE, battle.getId());
        LocalDateTime later = NOW.plusMinutes(30);
        assertThat(starts(later).cancelAbandoned(later)).isZero();
        assertThat(battle.getStatus()).isEqualTo(BattleStatus.IN_PROGRESS);
        assertThatThrownBy(() -> battle.cancelUnstarted(later))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("출발 시각이 10분 지나도 시작하지 못한 랜덤 대결(WAITING_START)은 CANCELLED")
    void waitingStartPastStartAt_isCancelled() {
        Battle random =
                battles.save(
                        Battle.random(
                                BattleFixtures.assigned(ALICE, StandardDistance.KM5),
                                BattleFixtures.assigned(BOB, StandardDistance.KM5),
                                NOW));
        random.scheduleStart(NOW.plusSeconds(13));
        LocalDateTime limit = NOW.plusSeconds(13).plus(Battle.ABANDON_AFTER);
        assertThat(random.isAbandoned(limit.minusSeconds(1))).isFalse();
        assertThat(starts(limit).cancelAbandoned(limit)).isEqualTo(1);
        assertThat(random.getStatus()).isEqualTo(BattleStatus.CANCELLED);
        assertThat(battles.existsActive(BOB)).isFalse();
    }

    @Test
    @DisplayName("새 기록 대결은 시작 보고 전의 이전 기록 대결을 대신하고(CANCELLED), 출발한 대결이 있으면 ALREADY_IN_BATTLE")
    void newRecordBattle_supersedesUnstarted_rejectsWhenRunning() {
        Battle first = selfBattle(NOW);
        Battle second = selfBattle(NOW.plusSeconds(5));
        assertThat(first.getStatus()).isEqualTo(BattleStatus.CANCELLED);
        assertThat(battles.findActive(ALICE)).containsExactly(second);

        records(NOW.plusSeconds(9)).markStarted(ALICE, second.getId());
        assertThatThrownBy(() -> selfBattle(NOW.plusSeconds(20)))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.ALREADY_IN_BATTLE);
        assertThat(battles.findActive(ALICE)).containsExactly(second);
    }
}
