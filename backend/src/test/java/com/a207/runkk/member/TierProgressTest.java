package com.a207.runkk.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.Distance;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * TierProgress 순번 규칙(D-37, D-40, 14장 "건너뛴 순번 (9 → 11), (2 → 11)"). 첫 배치(3회 창)는 배치고사로 옮겨 없어졌고
 * 재평가(10의 배수)만 남는다(J24-6, D-484).
 */
@org.junit.jupiter.api.DisplayName("TC-5734 티어 평가 순번 규칙(D-37, D-40, D-484)")
class TierProgressTest {

    @ParameterizedTest(name = "TC-5734 ({0}, {1}) → {2}")
    @CsvSource({
        "0, 1, 0",
        "1, 2, 0",
        "2, 3, 0",
        "2, 4, 0",
        "3, 9, 0",
        "9, 10, 10",
        "9, 11, 10",
        "2, 11, 10",
        "2, 10, 10",
        "10, 19, 0",
        "19, 20, 20",
        "15, 31, 30"
    })
    void reevaluationPointBetween_picksLargestTenth(int last, int next, int point) {
        OptionalInt result = TierProgress.reevaluationPointBetween(last, next);
        if (point == 0) {
            assertThat(result).isEmpty();
        } else {
            assertThat(result).hasValue(point);
        }
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5735 3회째는 평가가 없다(첫 배치 창 없음)")
    void evaluationBetween_noFirstPlacement() {
        assertThat(TierProgress.evaluationBetween(2, 3)).isEmpty();
        assertThat(TierProgress.evaluationBetween(9, 10))
                .contains(new TierEvaluation(EvaluationKind.REEVALUATION, 10));
        assertThat(TierProgress.evaluationBetween(15, 31))
                .contains(new TierEvaluation(EvaluationKind.REEVALUATION, 30));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5733 유효 3회째는 더 이상 첫 배치를 하지 않는다(평균 없음, J24-7)")
    void addValidRun_thirdRun_noAverage() {
        TierProgress progress = TierProgress.empty();
        assertThat(progress.addValidRun(input(30, 1))).isEmpty();
        assertThat(progress.addValidRun(input(36, 2))).isEmpty();
        assertThat(progress.addValidRun(input(42, 3))).isEmpty();
        assertThat(progress.totalValidDistance()).isEqualTo(Distance.ofKm(15));
    }

    @Test
    void addValidRun_tenthRunAveragesLastTen() {
        TierProgress progress = TierProgress.empty();
        OptionalDouble last = OptionalDouble.empty();
        for (int i = 1; i <= 10; i++) {
            last = progress.addValidRun(input(i * 2, i));
        }
        assertThat(last).hasValue(11.0);
    }

    @Test
    void addValidRun_sameOrdinalAgain_isIgnored() {
        TierProgress progress = TierProgress.empty();
        for (int i = 1; i <= 9; i++) {
            progress.addValidRun(input(30, i));
        }
        assertThat(progress.addValidRun(input(30, 10))).isPresent();

        assertThat(progress.addValidRun(input(99, 10))).isEmpty();
        assertThat(progress.totalValidDistance()).isEqualTo(Distance.ofKm(50));
        assertThat(progress.addValidRun(input(99, 2))).isEmpty();
    }

    @Test
    void addValidRun_skippedOrdinal_stillJudgesPassedPoint() {
        TierProgress progress = TierProgress.empty();
        for (int i = 1; i <= 9; i++) {
            progress.addValidRun(input(30, i));
        }
        // 10회째 반영이 실패하고 11회째가 왔다(D-40)
        assertThat(progress.addValidRun(input(40, 11))).hasValue(31.0);
    }

    private static RunScoreInput input(double score, int ordinal) {
        return new RunScoreInput(score, Distance.ofKm(5), ordinal);
    }
}
