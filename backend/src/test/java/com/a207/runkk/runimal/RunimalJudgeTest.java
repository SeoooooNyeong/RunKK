package com.a207.runkk.runimal;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.PersonalityTag;
import com.a207.runkk.common.TagOwner;
import com.a207.runkk.common.TagSet;
import com.a207.runkk.policy.RunimalRule;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
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
                crew);
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
    @DisplayName("임시 유형: 동행·코스는 태그, 거리·습관은 데이터")
    void provisional_usesTagsForCompanionAndCourse() {
        TagSet tags =
                TagSet.of(
                        Set.of(PersonalityTag.SOCIAL_RUN, PersonalityTag.EXPLORER_ROUTE),
                        TagOwner.RUNNER);
        RunSample s =
                sample(
                        List.of(
                                run(0, 2_000, 7, true, false),
                                run(1, 2_000, 7, true, false),
                                run(2, 2_000, 7, true, false)));
        Judged judged = judge.judgeProvisional(s, tags, RULE);
        assertThat(judged.type()).isEqualTo(RunimalType.EXPLORER_OTTER);
        assertThat(judged.ratios().ratioOfA(RunimalAxis.COMPANION)).isZero();
    }

    @Test
    @DisplayName("임시 유형: 태그가 없으면 데이터, 데이터도 없으면 A")
    void provisional_fallsBackToDataThenA() {
        RunSample s =
                sample(
                        List.of(
                                run(0, 8_000, 7, false, true),
                                run(1, 8_000, 15, false, true),
                                run(2, 8_000, 22, false, true)));
        Judged judged = judge.judgeProvisional(s, TagSet.empty(TagOwner.RUNNER), RULE);
        assertThat(judged.type()).isEqualTo(RunimalType.WILD_HORSE);

        Judged empty =
                judge.judgeProvisional(sample(List.of()), TagSet.empty(TagOwner.RUNNER), RULE);
        assertThat(empty.type()).isEqualTo(RunimalType.MORNING_SPARROW);
    }

    @Test
    @DisplayName("확정 유형: 4축 데이터, 코스 표본이 없으면 태그의 코스 값")
    void confirmed_allData_courseFallsBackToTag() {
        List<RunSample.SampleRun> runs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            runs.add(run(i, 2_000, 7, null, false));
        }
        TagSet familiar = TagSet.of(Set.of(PersonalityTag.FAMILIAR_ROUTE), TagOwner.RUNNER);
        assertThat(judge.judgeConfirmed(sample(runs), familiar, RULE).type())
                .isEqualTo(RunimalType.MORNING_SPARROW);
        assertThat(judge.judgeConfirmed(sample(runs), TagSet.empty(TagOwner.RUNNER), RULE).type())
                .isEqualTo(RunimalType.ALLEY_SQUIRREL);
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
