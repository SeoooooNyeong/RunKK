package com.a207.runkk.battle;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.policy.TierBoundary;
import com.a207.runkk.policy.VirtualRecordRule;
import com.a207.runkk.running.RunningScore;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** VirtualRecordFactory(14장 단위, A-21, srs.md 6.3 7.5, D-45 nicknameTaken). */
@DisplayName("VirtualRecordFactory 가상 기록 100개")
class VirtualRecordFactoryTest {

    static final TierBoundary BOUNDARY = new TierBoundary(List.of(30.0, 35.0, 40.0, 45.0));
    static final VirtualRecordRule RULE =
            new VirtualRecordRule(
                    27,
                    48,
                    List.of(-0.05, -0.025, 0.0, 0.025, 0.05),
                    List.of(
                            Distance.ofMeters(1_000),
                            Distance.ofMeters(3_000),
                            Distance.ofMeters(5_000),
                            Distance.ofMeters(10_000)));

    private static final double SCORE_TOLERANCE = 0.5;

    @Test
    @DisplayName("티어 5 × 거리 4 × 5개 = 100개, 티어·거리마다 5개")
    void generates100() {
        List<VirtualRecord> records =
                new VirtualRecordFactory(new Random(7)).generate(BOUNDARY, RULE, name -> false);
        assertThat(records).hasSize(100);
        assertThat(VirtualRecordFactory.expectedCount(RULE)).isEqualTo(100);
        for (Tier tier : VirtualRecordFactory.TIERS) {
            for (StandardDistance d : List.of(StandardDistance.KM1, StandardDistance.KM5)) {
                assertThat(records)
                        .filteredOn(r -> r.getTier() == tier && r.getDistance() == d)
                        .hasSize(5);
            }
        }
    }

    @Test
    @DisplayName("가운데 오프셋(0)의 기록은 티어 가운데 점수(골드 37.5)에 맞는다, 오프셋은 −5%~+5%")
    void middleOffset_matchesMidScore() {
        List<VirtualRecord> gold5k =
                new VirtualRecordFactory(new Random(7))
                        .generate(BOUNDARY, RULE, name -> false).stream()
                                .filter(r -> r.getTier() == Tier.GOLD)
                                .filter(r -> r.getDistance() == StandardDistance.KM5)
                                .toList();
        Duration middle = gold5k.get(2).getFinishTime();
        double score = RunningScore.of(Distance.ofMeters(5_000), middle).value();
        assertThat(Math.abs(score - 37.5)).isLessThan(SCORE_TOLERANCE);
        Duration fastest = gold5k.get(0).getFinishTime();
        Duration slowest = gold5k.get(4).getFinishTime();
        assertThat(fastest).isLessThan(middle);
        assertThat(slowest).isGreaterThan(middle);
        assertThat(fastest.toMillis())
                .isCloseTo(
                        Math.round(middle.toMillis() * 0.95),
                        org.assertj.core.data.Offset.offset(5_000L));
    }

    @Test
    @DisplayName("닉네임은 2~20자, 서로 다르고 회원 닉네임(nicknameTaken)과 겹치지 않는다")
    void nicknames_uniqueAndNotTaken() {
        Set<String> taken = new HashSet<>();
        List<VirtualRecord> first =
                new VirtualRecordFactory(new Random(1)).generate(BOUNDARY, RULE, name -> false);
        first.stream().limit(30).forEach(r -> taken.add(r.getNickname()));

        List<VirtualRecord> second =
                new VirtualRecordFactory(new Random(1)).generate(BOUNDARY, RULE, taken::contains);
        Set<String> names =
                second.stream().map(VirtualRecord::getNickname).collect(Collectors.toSet());
        assertThat(names).hasSize(100).doesNotContainAnyElementsOf(taken);
        assertThat(names).allMatch(n -> n.length() >= 2 && n.length() <= 20);
    }

    @Test
    @DisplayName("가상 기록은 균등 페이스 상대 표이고 비교 값의 가장 빠른 1km는 평균 페이스다")
    void opponentRecordBehaviour() {
        VirtualRecord record =
                new VirtualRecord(Tier.GOLD, StandardDistance.KM5, "달빛여우", Duration.ofMinutes(25));
        OpponentTrack track = record.track(StandardDistance.KM5.distance());
        assertThat(track.arrivalTime(StandardDistance.KM5.distance()))
                .contains(Duration.ofMinutes(25));
        assertThat(record.kind()).isEqualTo(OpponentKind.VIRTUAL);
        assertThat(record.displayTier()).isEqualTo(Tier.GOLD);
        ComparisonValues values = record.comparisonValues(StandardDistance.KM5.distance());
        assertThat(values.averagePace()).isEqualTo(values.fastestKm());
        assertThat(values.averagePace().secondsPerKm()).isEqualTo(300);
    }
}
