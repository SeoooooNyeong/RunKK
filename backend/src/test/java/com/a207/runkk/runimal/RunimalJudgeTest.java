package com.a207.runkk.runimal;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.Distance;
import com.a207.runkk.policy.RunimalRule;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** RunimalJudge와 축 평가 4개(14장 단위, A-14, srs.md 3.6.1 기준 값 50%·3km·60%·±2시간). */
@DisplayName("RunimalJudge와 축 평가")
class RunimalJudgeTest {

    static final RunimalRule RULE =
            new RunimalRule(0.5, Distance.ofMeters(3_000), 0.5, 0.6, Duration.ofHours(2), 0.6);
    private static final LocalDateTime DAY = LocalDateTime.of(2026, 10, 1, 7, 0);

    private final RunimalJudge judge =
            new RunimalJudge(
                    List.of(
                            new CompanionAxisEvaluator(),
                            new DistanceAxisEvaluator(),
                            new CourseAxisEvaluator(),
                            new HabitAxisEvaluator()));

    /** n개 표본: 거리, 시작 시각(시), 같은 코스, 크루런 연결. */
    private static RunSample.SampleRun run(
            int index, long meters, int hour, Boolean same, boolean crew) {
        return new RunSample.SampleRun(
                "r" + index,
                DAY.plusDays(index).withHour(hour),
                Distance.ofMeters(meters),
                same,
                crew,
                false);
    }

    private static RunSample sample(List<RunSample.SampleRun> runs) {
        return new RunSample(runs, runs);
    }

    @Nested
    @DisplayName("축 평가")
    class Axes {

        @Test
        @DisplayName("동행: 크루런 50% 미만이면 솔로(A), 50%면 소셜(B), 표본 없으면 비어 있음")
        void companion() {
            CompanionAxisEvaluator e = new CompanionAxisEvaluator();
            List<RunSample.SampleRun> runs = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                runs.add(run(i, 5_000, 7, null, i < 2));
            }
            double ratio = e.ratioOfA(sample(runs), RULE).getAsDouble();
            assertThat(ratio).isEqualTo(0.5);
            assertThat(e.lean(ratio, RULE)).isEqualTo(AxisLean.B);
            assertThat(e.lean(0.75, RULE)).isEqualTo(AxisLean.A);
            assertThat(e.ratioOfA(sample(List.of()), RULE)).isEmpty();
        }

        @Test
        @DisplayName("거리: 3km 미만이 50% 이상이면 단거리(A). 3km 정확히는 미만이 아니다")
        void distance() {
            DistanceAxisEvaluator e = new DistanceAxisEvaluator();
            RunSample s =
                    sample(List.of(run(0, 2_999, 7, null, false), run(1, 3_000, 7, null, false)));
            double ratio = e.ratioOfA(s, RULE).getAsDouble();
            assertThat(ratio).isEqualTo(0.5);
            assertThat(e.lean(ratio, RULE)).isEqualTo(AxisLean.A);
            assertThat(e.lean(0.49, RULE)).isEqualTo(AxisLean.B);
        }

        @Test
        @DisplayName("코스: 판정 없는 기록은 빼고 60% 이상이면 단골길(A), 판정이 하나도 없으면 비어 있음")
        void course() {
            CourseAxisEvaluator e = new CourseAxisEvaluator();
            RunSample s =
                    sample(
                            List.of(
                                    run(0, 5_000, 7, true, false),
                                    run(1, 5_000, 7, true, false),
                                    run(2, 5_000, 7, false, false),
                                    run(3, 5_000, 7, null, false)));
            double ratio = e.ratioOfA(s, RULE).getAsDouble();
            assertThat(ratio).isEqualTo(2.0 / 3);
            assertThat(e.lean(ratio, RULE)).isEqualTo(AxisLean.A);
            assertThat(e.lean(0.59, RULE)).isEqualTo(AxisLean.B);
            assertThat(e.ratioOfA(sample(List.of(run(0, 1, 7, null, false))), RULE)).isEmpty();
        }

        @Test
        @DisplayName("습관: 원형 중앙값 ±2시간. 23시·1시는 2시간 차이로 본다")
        void habit() {
            HabitAxisEvaluator e = new HabitAxisEvaluator();
            RunSample s =
                    sample(
                            List.of(
                                    run(0, 5_000, 23, null, false),
                                    run(1, 5_000, 0, null, false),
                                    run(2, 5_000, 1, null, false),
                                    run(3, 5_000, 12, null, false)));
            OptionalDouble ratio = e.ratioOfA(s, RULE);
            assertThat(ratio.getAsDouble()).isEqualTo(0.75);
            assertThat(e.lean(ratio.getAsDouble(), RULE)).isEqualTo(AxisLean.A);
            assertThat(HabitAxisEvaluator.circularGap(23 * 60, 60)).isEqualTo(120);
        }
    }

    @Test
    @DisplayName("TC-1188 임시 유형(태그 매개변수 없음): 데이터로 판정, 표본이 없으면 코스만 탐험길(D-147)")
    void provisional_fallsBackToDataThenA() {
        RunSample s =
                sample(
                        List.of(
                                run(0, 8_000, 7, false, true),
                                run(1, 8_000, 15, false, true),
                                run(2, 8_000, 22, false, true)));
        Judged judged = judge.judgeProvisional(s, RULE);
        assertThat(judged.type()).isEqualTo(RunimalType.WILD_HORSE);

        Judged empty = judge.judgeProvisional(sample(List.of()), RULE);
        assertThat(empty.type()).isEqualTo(RunimalType.ALLEY_SQUIRREL);
        for (RunimalAxis axis : RunimalAxis.values()) {
            assertThat(empty.ratios().ratioOfA(axis)).as(axis.name()).isEqualTo(0.5);
        }
    }

    @Test
    @DisplayName("TC-1186 확정 유형: 코스 표본이 없으면 탐험길(경로 파기만 남은 경우도 같다)")
    void confirmed_allData_courseFallsBackToTag() {
        List<RunSample.SampleRun> runs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            runs.add(run(i, 2_000, 7, null, false));
        }
        Judged judged = judge.judgeConfirmed(sample(runs), RULE);
        assertThat(judged.type()).isEqualTo(RunimalType.ALLEY_SQUIRREL);
        assertThat(judged.ratios().ratioOfA(RunimalAxis.COURSE)).isEqualTo(0.5);
        assertThat(judge.judgeProvisional(sample(runs.subList(0, 3)), RULE).type())
                .isEqualTo(RunimalType.ALLEY_SQUIRREL);
    }

    @Test
    @DisplayName("TC-1184 ③ 비교 대상이 없던 첫 러닝(거짓)도 표본: 1 ÷ 2 = 0.5 < 0.6 → 탐험길")
    void confirmed_noComparisonRunCountsAsNotSame() {
        List<RunSample.SampleRun> runs = new ArrayList<>();
        runs.add(run(0, 2_000, 7, false, false));
        runs.add(run(1, 2_000, 7, true, false));
        for (int i = 2; i < 10; i++) {
            runs.add(run(i, 2_000, 7, null, false));
        }
        Judged judged = judge.judgeConfirmed(sample(runs), RULE);
        assertThat(judged.ratios().ratioOfA(RunimalAxis.COURSE)).isEqualTo(0.5);
        assertThat(judged.type()).isEqualTo(RunimalType.ALLEY_SQUIRREL);
    }

    @Test
    @DisplayName("AxisEvaluator.leanWithoutSample: 코스만 B, 나머지 A(D-147, E-08)")
    void leanWithoutSample() {
        assertThat(new CourseAxisEvaluator().leanWithoutSample()).isEqualTo(AxisLean.B);
        assertThat(new CompanionAxisEvaluator().leanWithoutSample()).isEqualTo(AxisLean.A);
        assertThat(new DistanceAxisEvaluator().leanWithoutSample()).isEqualTo(AxisLean.A);
        assertThat(new HabitAxisEvaluator().leanWithoutSample()).isEqualTo(AxisLean.A);
    }

    @Test
    @DisplayName("유형 카드는 유형의 성향 쪽 비율을 보인다(솔로 78%)")
    void card_showsDominantPercent() {
        RunimalCardView card =
                RunimalCardView.of(RunimalType.LONE_WOLF, RunimalProfileTest.half(), false);
        assertThat(card.axes()).hasSize(4);
        assertThat(card.axes().get(0).label()).isEqualTo("솔로런");
        assertThat(card.axes().get(0).percent()).isEqualTo(75);
        assertThat(card.axes().get(1).label()).isEqualTo("장거리");
        assertThat(card.axes().get(1).percent()).isEqualTo(25);
    }
}
