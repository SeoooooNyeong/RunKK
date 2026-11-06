package com.a207.runkk.battle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.PlacementBasis;
import com.a207.runkk.policy.TierBoundary;
import com.a207.runkk.policy.VirtualRecordRule;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 배치고사 상대(05-21 A-38, D-480, srs J24-1·J24-2 표). 기대 페이스는 km당 초 ±2초. */
@org.junit.jupiter.api.DisplayName("PlacementOpponentFactory 배치고사 상대(A-38)")
class PlacementOpponentFactoryTest {

    private static final TierBoundary BOUNDARY = VirtualRecordFactoryTest.BOUNDARY;
    private static final VirtualRecordRule RULE = VirtualRecordFactoryTest.RULE;
    private static final double PACE_TOLERANCE = 2.0;

    private final InMemoryBattleStores.Virtuals virtuals = new InMemoryBattleStores.Virtuals();
    private final PlacementOpponentFactory factory =
            new PlacementOpponentFactory(virtuals, new Random(5));

    @BeforeEach
    void virtualRecords() {
        virtuals.replaceAll(
                new VirtualRecordFactory(new Random(7)).generate(BOUNDARY, RULE, n -> false));
    }

    private static double secondsPerKm(PlacementOpponent o, StandardDistance d) {
        return o.finishTime().toMillis() / 1000.0 / d.distance().km();
    }

    private PlacementOpponent first(StandardDistance d) {
        return factory.create(new PlacementBasis(1, null), d, BOUNDARY, RULE);
    }

    @ParameterizedTest(name = "TC-5722~TC-5725 1판 {0}: 점수 27의 일정 페이스 {1}초/km")
    @CsvSource({"KM1, 358", "KM3, 392", "KM5, 402", "KM10, 418"})
    void roundOne_finishTimeAtScore27(StandardDistance d, double expectedPace) {
        assertThat(
                        PlacementOpponentFactory.opponentScore(
                                new PlacementBasis(1, null), BOUNDARY, RULE))
                .isEqualTo(27.0);
        assertThat(secondsPerKm(first(d), d)).isCloseTo(expectedPace, within(PACE_TOLERANCE));
    }

    @ParameterizedTest(name = "TC-5726·TC-5727 2·3판 임시 {0}: 가운데 점수 {1}, 5km {2}초/km")
    @CsvSource({
        "BRONZE, 28.5, 384",
        "SILVER, 32.5, 344",
        "GOLD, 37.5, 305",
        "PLATINUM, 42.5, 275",
        "MASTER, 46.5, 255"
    })
    void laterRounds_useProvisionalMidScore(Tier tier, double score, double pace5k) {
        PlacementBasis basis = new PlacementBasis(2, tier);
        assertThat(PlacementOpponentFactory.opponentScore(basis, BOUNDARY, RULE)).isEqualTo(score);
        PlacementOpponent opponent = factory.create(basis, StandardDistance.KM5, BOUNDARY, RULE);
        assertThat(secondsPerKm(opponent, StandardDistance.KM5))
                .isCloseTo(pace5k, within(PACE_TOLERANCE));
        assertThat(
                        secondsPerKm(
                                factory.create(
                                        new PlacementBasis(3, tier),
                                        StandardDistance.KM5,
                                        BOUNDARY,
                                        RULE),
                                StandardDistance.KM5))
                .as("3판도 같은 규칙")
                .isCloseTo(pace5k, within(PACE_TOLERANCE));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5728 2·3판인데 임시 티어가 없으면 IllegalStateException")
    void laterRoundWithoutProvisionalTier_throws() {
        assertThatThrownBy(
                        () ->
                                PlacementOpponentFactory.opponentScore(
                                        new PlacementBasis(2, null), BOUNDARY, RULE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5729 만드는 순간 넘겨받은 경계·규칙을 쓴다(뒤에 바뀌어도 만든 상대는 그대로, J24-13)")
    void usesRuleAndBoundaryPassedAtCreation() {
        VirtualRecordRule lower =
                new VirtualRecordRule(25, 48, RULE.paceOffsets(), RULE.distances());
        PlacementOpponent before = first(StandardDistance.KM5);
        PlacementOpponent after =
                factory.create(new PlacementBasis(1, null), StandardDistance.KM5, BOUNDARY, lower);

        assertThat(after.finishTime()).isGreaterThan(before.finishTime());
        assertThat(secondsPerKm(before, StandardDistance.KM5)).isCloseTo(402, within(2.0));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5730 상대 표는 일정 페이스(구간마다 같은 속도)이고 대결 거리에서 끝난다")
    void opponentTrack_isUniformPace() {
        PlacementOpponent o = first(StandardDistance.KM5);
        OpponentTrack track = o.track(Distance.ofMeters(5_000));
        Duration finish = o.finishTime();

        assertThat(track.arrivalTime(Distance.ofMeters(5_000))).contains(finish);
        assertThat(track.distanceAt(finish.dividedBy(2)).meters()).isCloseTo(2_500L, within(5L));
        assertThat(track.distanceAt(finish.dividedBy(5)).meters()).isCloseTo(1_000L, within(5L));
        assertThat(o.comparisonValues(Distance.ofMeters(5_000)).averagePaceValue())
                .isEqualTo(o.comparisonValues(Distance.ofMeters(5_000)).fastestKmValue());
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5731 상대 티어는 숨기고(UNRANKED), 가상 기록 종류, 기록 ID는 PLACEMENT-판, 닉네임은 같은 거리 가상 기록에서 빌린다")
    void opponent_hidesTierAndBorrowsVirtualNickname() {
        PlacementOpponent o =
                factory.create(
                        new PlacementBasis(2, Tier.GOLD), StandardDistance.KM3, BOUNDARY, RULE);

        assertThat(o.displayTier()).isEqualTo(Tier.UNRANKED);
        assertThat(o.kind()).isEqualTo(OpponentKind.VIRTUAL);
        assertThat(o.recordId()).isEqualTo("PLACEMENT-2");
        List<DisplayName> golds =
                virtuals.findByTierAndDistance(Tier.GOLD, StandardDistance.KM3).stream()
                        .map(VirtualRecord::displayName)
                        .toList();
        assertThat(golds).contains(o.displayName());
        List<DisplayName> bronzes =
                virtuals.findByTierAndDistance(Tier.BRONZE, StandardDistance.KM1).stream()
                        .map(VirtualRecord::displayName)
                        .toList();
        assertThat(bronzes)
                .as("1판은 브론즈 가상 기록 이름")
                .contains(first(StandardDistance.KM1).displayName());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5732 닉네임을 빌릴 가상 기록이 없으면 MATCH_FAILED")
    void noVirtualRecord_failsWithMatchFailed() {
        virtuals.replaceAll(List.of());

        assertThatThrownBy(() -> first(StandardDistance.KM5))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.MATCH_FAILED);
    }
}
