package com.a207.runkk.runbody;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 러닝 선택 G-30 러닝 점수 규칙. */
@DisplayName("러닝 점수는 점수 있는 축의 평균 반올림이다")
class RunBodyScoreTest {

    @Test
    void overall_isRoundedMeanOfScoredAxes() {
        assertThat(RunBodyScore.overall(List.of(78, 84, 72, 80))).as("314 / 4 = 78.5").isEqualTo(79);
        assertThat(RunBodyScore.overall(Arrays.asList(78, 84, 72, null, 80)))
                .as("미산출 축(null)은 빼고 센다")
                .isEqualTo(79);
        assertThat(RunBodyScore.overall(List.of(78, 39, 46, 62, 69))).as("294 / 5 = 58.8").isEqualTo(59);
        assertThat(RunBodyScore.overall(List.of(0, 100))).isEqualTo(50);
    }

    @Test
    void overall_isNullWithoutScores_andRejectsOutOfRange() {
        assertThat(RunBodyScore.overall(List.of())).isNull();
        assertThat(RunBodyScore.overall(Arrays.asList(null, null))).isNull();
        assertThatThrownBy(() -> RunBodyScore.overall(List.of(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void change_needsBothScores() {
        assertThat(RunBodyScore.change(78, 72)).isEqualTo(6);
        assertThat(RunBodyScore.change(70, 76)).isEqualTo(-6);
        assertThat(RunBodyScore.change(null, 76)).isNull();
        assertThat(RunBodyScore.change(78, null)).isNull();
    }
}
