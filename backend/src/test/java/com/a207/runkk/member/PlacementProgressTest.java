package com.a207.runkk.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.policy.TierBoundary;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 배치고사 진행(05-21 2.1, A-37, D-483): 1·2판 임시 티어, 3판 확정, 같은 대결 두 번 셈 금지, 완료 뒤 무시, 정정. */
@DisplayName("PlacementProgress 판 셈(A-37)")
class PlacementProgressTest {

    private static final TierBoundary BOUNDARY = new TierBoundary(List.of(30.0, 35.0, 40.0, 45.0));
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 4, 9, 0);

    static PlacementRoundInput input(long battleId, double score, int ordinal) {
        return input(battleId, score, StandardDistance.KM5, ordinal);
    }

    static PlacementRoundInput input(
            long battleId, double score, StandardDistance distance, int ordinal) {
        return new PlacementRoundInput(
                battleId,
                score,
                distance,
                distance.distance(),
                Duration.ofMinutes(30),
                new Pace(360),
                PlacementVerdict.WIN,
                ordinal,
                T0.plusMinutes(ordinal));
    }

    @Test
    @DisplayName("TC-5709 시작은 0판, 다음 판 1, 임시 티어 없음, 완료 아님")
    void start_isEmpty() {
        PlacementProgress p = PlacementProgress.start(7L);

        assertThat(p.getMemberId()).isEqualTo(7L);
        assertThat(p.countedRounds()).isZero();
        assertThat(p.nextRound()).isEqualTo(1);
        assertThat(p.provisionalTier()).isEmpty();
        assertThat(p.isCompleted()).isFalse();
        assertThat(p.basis()).isEqualTo(new PlacementBasis(1, null));
        assertThat(p.rounds()).isEmpty();
        assertThat(PlacementProgress.ROUNDS).isEqualTo(3);
    }

    @Test
    @DisplayName(
            "TC-5710·TC-5711·TC-5713·TC-5719·TC-5808·TC-5809 1판 = 그 판 점수의 티어, 2판 = 두 판 평균, 3판 = 세 판"
                    + " 평균으로 확정")
    void count_provisionalThenConfirmed() {
        PlacementProgress p = PlacementProgress.start(7L);

        assertThat(p.count(input(11, 27, 4), BOUNDARY))
                .isEqualTo(new PlacementCount.Provisional(1, Tier.BRONZE));
        assertThat(p.provisionalTier()).contains(Tier.BRONZE);
        assertThat(p.basis()).isEqualTo(new PlacementBasis(2, Tier.BRONZE));

        assertThat(p.count(input(12, 41, 5), BOUNDARY))
                .isEqualTo(new PlacementCount.Provisional(2, Tier.SILVER)); // 평균 34
        assertThat(p.count(input(13, 47, 6), BOUNDARY))
                .isEqualTo(new PlacementCount.Confirmed(Tier.GOLD)); // 평균 38.33

        assertThat(p.isCompleted()).isTrue();
        assertThat(p.provisionalTier()).isEmpty();
        assertThat(p.countedRounds()).isEqualTo(3);
        assertThat(p.confirmedRunOrdinal()).hasValue(6);
        assertThat(p.completedAt()).contains(T0.plusMinutes(6));
        assertThat(p.rounds()).extracting(PlacementRound::round).containsExactly(1, 2, 3);
        assertThatThrownBy(p::nextRound).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("TC-5716 거리가 다른 판도 점수로 평균한다(기존 규칙)")
    void count_mixedDistances_averageScores() {
        PlacementProgress p = PlacementProgress.start(7L);
        p.count(input(1, 30, StandardDistance.KM1, 1), BOUNDARY);
        p.count(input(2, 40, StandardDistance.KM10, 2), BOUNDARY);

        assertThat(p.count(input(3, 35, StandardDistance.KM3, 3), BOUNDARY))
                .isEqualTo(new PlacementCount.Confirmed(Tier.GOLD)); // 평균 35
        assertThat(p.rounds())
                .extracting(PlacementRound::battleDistance)
                .containsExactly(StandardDistance.KM1, StandardDistance.KM10, StandardDistance.KM3);
    }

    @Test
    @DisplayName("TC-5717·TC-5807 같은 대결을 다시 세면 Ignored, 진행은 그대로")
    void count_sameBattle_ignored() {
        PlacementProgress p = PlacementProgress.start(7L);
        p.count(input(11, 27, 1), BOUNDARY);

        assertThat(p.count(input(11, 45, 2), BOUNDARY)).isInstanceOf(PlacementCount.Ignored.class);
        assertThat(p.countedRounds()).isEqualTo(1);
        assertThat(p.hasCounted(11)).isTrue();
        assertThat(p.hasCounted(12)).isFalse();
    }

    @Test
    @DisplayName("TC-5718·TC-5806 완료 뒤 판은 Ignored")
    void count_afterCompletion_ignored() {
        PlacementProgress p = PlacementProgress.start(7L);
        p.count(input(1, 30, 1), BOUNDARY);
        p.count(input(2, 30, 2), BOUNDARY);
        p.count(input(3, 30, 3), BOUNDARY);

        assertThat(p.count(input(4, 45, 4), BOUNDARY)).isInstanceOf(PlacementCount.Ignored.class);
        assertThat(p.rounds()).hasSize(3);
    }

    @Test
    @DisplayName("TC-5720 판정 정정은 그 판의 승패만 바꾸고, 없는 판이면 거짓")
    void correctVerdict_changesOnlyThatRound() {
        PlacementProgress p = PlacementProgress.start(7L);
        p.count(input(1, 30, 1), BOUNDARY);

        assertThat(p.correctVerdict(1, PlacementVerdict.LOSE)).isTrue();
        assertThat(p.correctVerdict(99, PlacementVerdict.LOSE)).isFalse();
        assertThat(p.rounds().get(0).verdict()).isEqualTo(PlacementVerdict.LOSE);
        assertThat(p.countedRounds()).isEqualTo(1);
    }

    @Test
    @DisplayName("TC-5721 판 기록 값을 그대로 남긴다(거리·시간·페이스·순번)")
    void round_keepsRecordValues() {
        PlacementProgress p = PlacementProgress.start(7L);
        p.count(input(1, 30, 4), BOUNDARY);

        PlacementRound r = p.rounds().get(0);
        assertThat(r.battleId()).isEqualTo(1L);
        assertThat(r.score()).isEqualTo(30.0);
        assertThat(r.runDistance()).isEqualTo(Distance.ofKm(5));
        assertThat(r.runElapsed()).isEqualTo(Duration.ofMinutes(30));
        assertThat(r.averagePace()).contains(new Pace(360));
        assertThat(r.validRunOrdinal()).isEqualTo(4);
        assertThat(r.countedAt()).isEqualTo(T0.plusMinutes(4));
        assertThatThrownBy(() -> p.rounds().clear())
                .as("불변 사본")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("TC-5712 임시 티어 경계: 1판 29.99는 브론즈, 30.0은 실버")
    void provisionalTier_boundaryThirty() {
        PlacementProgress below = PlacementProgress.start(1L);
        assertThat(below.count(input(1, 29.99, 1), BOUNDARY))
                .isEqualTo(new PlacementCount.Provisional(1, Tier.BRONZE));
        PlacementProgress at = PlacementProgress.start(2L);
        assertThat(at.count(input(2, 30.0, 1), BOUNDARY))
                .isEqualTo(new PlacementCount.Provisional(1, Tier.SILVER));
    }

    @Test
    @DisplayName("TC-5714 확정 경계: 세 판 평균 45.0은 마스터, 44.99는 플래티넘")
    void confirmedTier_boundaryFortyFive() {
        PlacementProgress master = PlacementProgress.start(1L);
        master.count(input(1, 45, 1), BOUNDARY);
        master.count(input(2, 45, 2), BOUNDARY);
        assertThat(master.count(input(3, 45, 3), BOUNDARY))
                .isEqualTo(new PlacementCount.Confirmed(Tier.MASTER));
        PlacementProgress platinum = PlacementProgress.start(2L);
        platinum.count(input(4, 45, 1), BOUNDARY);
        platinum.count(input(5, 45, 2), BOUNDARY);
        assertThat(platinum.count(input(6, 44.97, 3), BOUNDARY))
                .isEqualTo(new PlacementCount.Confirmed(Tier.PLATINUM));
    }

    @Test
    @DisplayName("TC-5715 확정은 승패와 상관없다: 세 판 모두 져도 점수 평균으로 정한다(브리프 10)")
    void confirmation_ignoresVerdicts() {
        PlacementProgress p = PlacementProgress.start(1L);
        for (int i = 1; i <= 3; i++) {
            PlacementRoundInput lost =
                    new PlacementRoundInput(
                            i,
                            41,
                            StandardDistance.KM5,
                            StandardDistance.KM5.distance(),
                            Duration.ofMinutes(30),
                            null,
                            PlacementVerdict.LOSE,
                            i,
                            T0);
            p.count(lost, BOUNDARY);
        }
        assertThat(p.isCompleted()).isTrue();
        assertThat(p.rounds()).allMatch(r -> r.verdict() == PlacementVerdict.LOSE);
        assertThat(p.rounds().get(0).averagePace()).isEmpty();
    }
}
