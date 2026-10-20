package com.a207.runkk.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.Distance;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** TierProgress 순번 규칙(D-37, D-40, 14장 "건너뛴 순번 (2 → 4), (9 → 11), (2 → 11)"). */
class TierProgressTest {

    @ParameterizedTest(name = "({0}, {1}) → {2}")
    @CsvSource({
        "0, 1, 0",
        "1, 2, 0",
        "2, 3, 3",
        "2, 4, 3",
        "3, 9, 0",
        "9, 10, 10",
        "9, 11, 10",
        "2, 11, 10",
        "2, 10, 10",
        "10, 19, 0",
        "19, 20, 10",
        "15, 31, 10"
    })
    void evaluationWindowBetween_picksLargestJudgementPoint(int last, int next, int window) {
        OptionalInt result = TierProgress.evaluationWindowBetween(last, next);
        if (window == 0) {
            assertThat(result).isEmpty();
        } else {
            assertThat(result).hasValue(window);
        }
    }

    @Test
    void addValidRun_thirdRunAveragesThreeScores() {
        TierProgress progress = TierProgress.empty();
        assertThat(progress.addValidRun(input(30, 1))).isEmpty();
        assertThat(progress.addValidRun(input(36, 2))).isEmpty();
        assertThat(progress.addValidRun(input(42, 3))).hasValue(36.0);
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
        progress.addValidRun(input(30, 1));
        progress.addValidRun(input(30, 2));
        assertThat(progress.addValidRun(input(30, 3))).isPresent();

        assertThat(progress.addValidRun(input(99, 3))).isEmpty();
        assertThat(progress.totalValidDistance()).isEqualTo(Distance.ofKm(15));
        assertThat(progress.addValidRun(input(99, 2))).isEmpty();
    }

    @Test
    void addValidRun_skippedOrdinal_stillJudgesPassedPoint() {
        TierProgress progress = TierProgress.empty();
        progress.addValidRun(input(30, 1));
        progress.addValidRun(input(30, 2));
        // 3회째 반영이 실패하고 4회째가 왔다(D-40)
        assertThat(progress.addValidRun(input(36, 4))).hasValue(32.0);
    }

    private static RunScoreInput input(double score, int ordinal) {
        return new RunScoreInput(score, Distance.ofKm(5), ordinal);
    }
}
