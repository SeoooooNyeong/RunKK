package com.a207.runkk.battle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.battle.live.OpponentProgress;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.ProgressReport;
import com.a207.runkk.common.Tier;
import com.a207.runkk.running.CurvePoint;
import com.a207.runkk.running.RunComparison;
import com.a207.runkk.running.RunCurve;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * battle 값 객체·다형성 열거형(14장 단위 1단계): OpponentTrack 보조 연산, BattleType·ComparisonItem,
 * LiveBattleSession(A-09).
 */
@org.junit.jupiter.api.DisplayName("battle 값 객체와 열거형")
class BattleValuesTest {

    private static final LocalDateTime NOW = BattleFixtures.NOW;
    private static final Distance FIVE_K = Distance.ofMeters(5_000);

    private static ProgressReport report(long meters) {
        return new ProgressReport(Distance.ofMeters(meters), Duration.ofSeconds(60), NOW);
    }

    private static LiveBattleSession session() {
        return LiveBattleSession.open(
                1L, FIVE_K, Map.of(1L, DisplayName.of("앨리스"), 2L, DisplayName.of("밥")), NOW);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("OpponentTrack: 저장 형식 왕복, 줄어드는 표 거부, 앱 전송 형식은 (0,0)부터 초 단위")
    void opponentTrack_helpers() {
        OpponentTrack track = OpponentTrack.uniform(FIVE_K, Duration.ofMillis(1_501_300));
        assertThat(OpponentTrack.decode(track.encode())).isEqualTo(track);
        assertThat(OpponentTrack.decode(null).isEmpty()).isTrue();
        assertThatThrownBy(
                        () ->
                                OpponentTrack.of(
                                        List.of(
                                                new TrackSample(Duration.ofSeconds(2), FIVE_K),
                                                new TrackSample(Duration.ofSeconds(1), FIVE_K))))
                .isInstanceOf(IllegalArgumentException.class);
        List<TrackSample> seconds = track.wholeSeconds();
        assertThat(seconds.get(0).elapsed()).isZero();
        assertThat(seconds.get(seconds.size() - 1).distance()).isEqualTo(FIVE_K);
        assertThat(seconds).extracting(TrackSample::elapsed).isSorted().doesNotHaveDuplicates();
        assertThat(track.fastestKm()).isPresent();
        assertThatThrownBy(() -> OpponentTrack.uniform(FIVE_K, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "RunRecordOpponent: 좌표 없는 표본을 대결 거리에서 자르고 비교 값은 RunComparison 그대로")
    void runRecordOpponent() {
        RunRecordOpponent opponent =
                new RunRecordOpponent(
                        "rec",
                        new RunComparison(Duration.ofMinutes(50), new Pace(300), null),
                        new RunCurve(
                                List.of(
                                        new CurvePoint(Duration.ZERO, Distance.ZERO),
                                        new CurvePoint(Duration.ofMinutes(25), FIVE_K),
                                        new CurvePoint(
                                                Duration.ofMinutes(50), Distance.ofMeters(10_000))),
                                Distance.ofMeters(10_000)),
                        new RunRecordOpponent.Owner(DisplayName.of("10/03의 나"), Tier.GOLD));
        OpponentTrack track = opponent.track(FIVE_K);
        assertThat(track.samples()).last().extracting(TrackSample::distance).isEqualTo(FIVE_K);
        assertThat(opponent.comparisonValues(FIVE_K).fastestKmValue()).isEmpty();
        assertThat(opponent.kind()).isEqualTo(OpponentKind.RUN_RECORD);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "BattleType: 비교표 항목·이력 분류·시작 방식(6.4), 비교표는 공개 안 된 값을 \"공개 안 됨\"으로")
    void battleTypeAndComparison() {
        assertThat(BattleType.GHOST.historyGroup()).isEqualTo(BattleType.RANDOM);
        assertThat(BattleType.GHOST.comparisonItems()).contains(ComparisonItem.OVERTAKES);
        assertThat(BattleType.SELF.comparisonItems()).contains(ComparisonItem.MAX_LEAD);
        assertThat(BattleType.CELEBRITY.comparisonItems()).contains(ComparisonItem.MAX_GAP);
        assertThat(BattleType.SELF.startsWithCountdown()).isTrue();
        assertThat(BattleType.RANDOM.startsWithCountdown()).isFalse();
        assertThat(BattleType.RANDOM.isAgainstRecord()).isFalse();

        Battle battle = BattleFixtures.startedRandom();
        BattleParticipant me = battle.participant(BattleFixtures.ALICE).orElseThrow();
        battle.recordOvertakes(BattleFixtures.ALICE, 2);
        ComparisonTable table =
                ComparisonTable.of(
                        BattleType.RANDOM,
                        new RunComparison(Duration.ofSeconds(3_725), new Pace(330), new Pace(310)),
                        ComparisonValues.undisclosed(),
                        me);
        assertThat(table.rows())
                .extracting(ComparisonTable.Row::mine)
                .containsExactly("1:02:05", "5'30\"", "5'10\"", "2회");
        assertThat(table.rows())
                .extracting(ComparisonTable.Row::theirs)
                .containsOnly(ComparisonItem.NOT_DISCLOSED);
        battle.recordGaps(BattleFixtures.ALICE, Distance.ofMeters(40), Distance.ofMeters(15));
        assertThat(ComparisonItem.MAX_LEAD.mine(new RunComparison(null, null, null), me))
                .isEqualTo("40m");
        assertThat(
                        ComparisonItem.MAX_LEAD.theirs(
                                ComparisonValues.undisclosed().withMaxLead(Distance.ofMeters(15))))
                .isEqualTo("15m");
        assertThat(ComparisonItem.RECORD_TIME.mine(new RunComparison(null, null, null), me))
                .isEqualTo("-");
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "LiveBattleSession: 10m 이하 차이의 선두 바뀜은 추월이 아니다, 도착은 한 번, 저장 왕복")
    void liveSession_overtakeMarginArrivalAndSnapshot() {
        LiveBattleSession s = session();
        s.onProgress(1L, report(100), NOW);
        s.onProgress(2L, report(85), NOW);
        List<BattleDelivery> small = s.onProgress(2L, report(105), NOW);
        assertThat(small).hasSize(1);
        List<BattleDelivery> overtake = s.onProgress(2L, report(120), NOW);
        assertThat(overtake).hasSize(3);
        assertThat(s.overtakeCounts()).containsEntry(2L, 1).containsEntry(1L, 0);

        s.onProgress(2L, report(5_000), NOW);
        assertThat(s.newArrival()).contains(2L);
        s.onProgress(2L, report(5_010), NOW);
        assertThat(s.newArrival()).isEmpty();
        assertThat(s.onProgress(3L, report(1), NOW)).isEmpty();

        LiveBattleSession copy = LiveBattleSession.restore(s.snapshot());
        assertThat(copy.overtakeCounts()).isEqualTo(s.overtakeCounts());
        assertThat(copy.nameOf(1L)).contains(DisplayName.of("앨리스"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("LiveBattleSession: 늦게 온 이전 진행값(더 작은 거리)은 거리를 되돌리지 않는다")
    void liveSession_ignoresRegression() {
        LiveBattleSession s = session();
        s.onProgress(1L, report(500), NOW);
        s.onProgress(1L, report(450), NOW.plusSeconds(1));
        List<BattleDelivery> out = s.onProgress(2L, report(100), NOW.plusSeconds(1));
        assertThat(((OpponentProgress) out.get(0).message()).opponentDistance())
                .isEqualTo(Distance.ofMeters(100));
        assertThat(s.snapshot().runners())
                .filteredOn(r -> r.memberId() == 1L)
                .extracting(LiveBattleSession.Runner::distanceMeters)
                .containsExactly(500L);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "LiveBattleSession: 3초 넘게 조용한 참가자만 끊김, 도착한 참가자는 제외, 다시 오면 복구 표시")
    void liveSession_timeouts() {
        LiveBattleSession s = session();
        assertThat(s.detectTimeouts(NOW.plusSeconds(3))).isEmpty();
        assertThat(s.detectTimeouts(NOW.plusSeconds(4))).hasSize(2);
        assertThat(s.timedOut()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(s.detectTimeouts(NOW.plusSeconds(10))).isEmpty();
        s.onProgress(1L, report(5_000), NOW.plusSeconds(11));
        assertThat(s.reconnected()).contains(1L);
        assertThat(s.detectTimeouts(NOW.plusSeconds(30))).isEmpty();
        assertThatThrownBy(
                        () ->
                                LiveBattleSession.open(
                                        1L, FIVE_K, Map.of(1L, DisplayName.of("혼자")), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
