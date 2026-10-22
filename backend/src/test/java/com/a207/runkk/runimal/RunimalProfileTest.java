package com.a207.runkk.runimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * RunimalProfile.onValidRun 순번 표(14장 단위, D-37, D-40). 건너뛴 순번 행 (2 → 4), (9 → 11), (2 → 11)과 같은 순번
 * 재전달을 포함한다. 상태도 2.4.11의 전이도 확인한다.
 */
@DisplayName("RunimalProfile 판정 순번")
class RunimalProfileTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 7, 0);

    static AxisRatios half() {
        Map<RunimalAxis, Double> ratios = new EnumMap<>(RunimalAxis.class);
        for (RunimalAxis axis : RunimalAxis.values()) {
            ratios.put(axis, 0.75);
        }
        return new AxisRatios(ratios);
    }

    private static RunimalProfile at(int previousOrdinal) {
        RunimalProfile profile = RunimalProfile.create(1L);
        if (previousOrdinal > 0) {
            profile.onValidRun(previousOrdinal);
        }
        return profile;
    }

    @ParameterizedTest(name = "직전 {0} → 새 {1}: {2}")
    @DisplayName("순번 표: 직전 반영 순번 < 판정 지점 ≤ 새 순번이면 판정, 여럿이면 가장 큰 지점")
    @CsvSource({
        "0, 1, NONE",
        "1, 2, NONE",
        "2, 3, PROVISIONAL",
        "3, 4, NONE",
        "5, 9, NONE",
        "9, 10, CONFIRM",
        "10, 11, NONE",
        "19, 20, CONFIRM",
        "2, 4, PROVISIONAL",
        "9, 11, CONFIRM",
        "2, 11, CONFIRM",
        "0, 3, PROVISIONAL",
        "3, 3, NONE",
        "10, 10, NONE",
        "11, 10, NONE"
    })
    void onValidRun_table(int previous, int next, JudgeTrigger expected) {
        RunimalProfile profile = at(previous);
        assertThat(profile.onValidRun(next)).isEqualTo(expected);
        assertThat(profile.getValidRunCount()).isEqualTo(Math.max(previous, next));
    }

    @ParameterizedTest(name = "횟수 {0}: 남은 {1}")
    @DisplayName("remainingToNextJudgement: 3 미만이면 3 − n, 아니면 10 − (n mod 10)(FR-154)")
    @CsvSource({"0, 3", "2, 1", "3, 7", "9, 1", "10, 10", "13, 7"})
    void remaining(int count, int expected) {
        assertThat(at(count).remainingToNextJudgement()).isEqualTo(expected);
    }

    @Test
    @DisplayName("상태 전이: NONE → PROVISIONAL → CONFIRMED, 판정 지점이 아니면 배정 거부")
    void transitions() {
        RunimalProfile profile = at(0);
        assertThat(profile.card()).isEmpty();
        assertThatThrownBy(() -> profile.assignProvisional(RunimalType.LONE_WOLF, half(), NOW))
                .isInstanceOf(IllegalStateException.class);

        profile.onValidRun(3);
        profile.assignProvisional(RunimalType.MORNING_SPARROW, half(), NOW);
        assertThat(profile.currentType()).contains(RunimalType.MORNING_SPARROW);
        assertThat(profile.card().orElseThrow().provisional()).isTrue();

        profile.onValidRun(10);
        assertThat(profile.confirm(RunimalType.LONE_WOLF, half(), NOW)).isTrue();
        assertThat(profile.currentType()).contains(RunimalType.LONE_WOLF);
        assertThat(profile.card().orElseThrow().provisional()).isFalse();

        profile.onValidRun(20);
        assertThat(profile.confirm(RunimalType.LONE_WOLF, half(), NOW)).isFalse();
        assertThatThrownBy(() -> profile.confirm(RunimalType.LONE_WOLF, half(), NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("앞 판정이 유실돼도 10회째에 바로 확정(NONE → CONFIRMED)")
    void confirmWithoutProvisional() {
        RunimalProfile profile = at(2);
        assertThat(profile.onValidRun(11)).isEqualTo(JudgeTrigger.CONFIRM);
        profile.confirm(RunimalType.WILD_HORSE, half(), NOW);
        assertThat(profile.getProvisionalType()).isEmpty();
        assertThat(profile.getConfirmedType()).contains(RunimalType.WILD_HORSE);
    }
}
