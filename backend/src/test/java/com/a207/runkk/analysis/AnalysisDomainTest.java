package com.a207.runkk.analysis;

import static com.a207.runkk.analysis.AnalysisFixtures.run;
import static com.a207.runkk.analysis.AnalysisFixtures.summary;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.Tier;
import com.a207.runkk.common.Week;
import com.a207.runkk.running.RunSummary;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * analysis 값·엔티티(14장 단위: Streak, Grass, 목표·계획 A-20, FitnessSummary, Report, RankingSnapshot A-19,
 * 규칙 문구).
 */
@org.junit.jupiter.api.DisplayName("analysis 도메인")
class AnalysisDomainTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 9, 0);

    @Nested
    @org.junit.jupiter.api.DisplayName("Streak.countBack")
    class StreakTest {
        @Test
        @org.junit.jupiter.api.DisplayName("오늘 기록이 있으면 오늘부터, 없으면 어제부터 끊길 때까지")
        void countsBackFromBaseOrYesterday() {
            Set<LocalDate> days = Set.of(MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(2));
            assertThat(Streak.countBack(days, MONDAY.plusDays(2))).isEqualTo(3);
            assertThat(Streak.countBack(days, MONDAY.plusDays(3))).isEqualTo(3);
            assertThat(Streak.countBack(days, MONDAY.plusDays(4))).isZero();
            assertThat(Streak.countBack(Set.of(), MONDAY)).isZero();
        }

        @Test
        @org.junit.jupiter.api.DisplayName(
                "주 단위(월요일 시작): 이번 주에 기록이 있으면 이번 주부터, 없으면 지난주부터 끊길 때까지(2026-10-31 결정)")
        void countsWeeksBackFromBaseWeekOrLastWeek() {
            // 지지난주 일요일, 지난주 월요일, 이번 주 수요일: 주마다 하루씩이면 이어진다
            Set<LocalDate> days =
                    Set.of(MONDAY.minusDays(8), MONDAY.minusDays(7), MONDAY.plusDays(2));
            assertThat(Streak.countWeeksBack(days, MONDAY.plusDays(6))).isEqualTo(3);
            assertThat(Streak.countWeeksBack(days, MONDAY.plusDays(2))).isEqualTo(3);
            assertThat(Streak.countWeeksBack(days, MONDAY))
                    .as("base 뒤의 기록은 세지 않아 이번 주 기록 전이면 지난주부터")
                    .isEqualTo(2);
            assertThat(Streak.countWeeksBack(days, MONDAY.plusDays(7)))
                    .as("이번 주(다음 주 월요일) 기록이 없어도 지난주부터 센다")
                    .isEqualTo(3);
            assertThat(Streak.countWeeksBack(days, MONDAY.plusDays(14))).as("한 주를 쉬면 끊긴다").isZero();
            assertThat(Streak.countWeeksBack(Set.of(MONDAY.minusDays(14), MONDAY), MONDAY))
                    .as("지난주가 비면 이번 주만")
                    .isEqualTo(1);
            assertThat(Streak.countWeeksBack(Set.of(), MONDAY)).isZero();
        }
    }

    @Nested
    @org.junit.jupiter.api.DisplayName("Grass")
    class GrassTest {
        @Test
        @org.junit.jupiter.api.DisplayName("12주 모든 날짜를 채우고 진하기는 최댓값 대비 0~4단계")
        void fillsRangeAndScalesIntensity() {
            DateRange range = Grass.rangeEndingOn(MONDAY.plusDays(2));
            Grass grass =
                    Grass.of(
                            Map.of(
                                    MONDAY,
                                    Distance.ofKm(10),
                                    MONDAY.plusDays(1),
                                    Distance.ofKm(2),
                                    LocalDate.of(2020, 1, 1),
                                    Distance.ofKm(99)),
                            range);
            assertThat(grass.cells()).hasSize((int) range.days().count());
            assertThat(grass.cells().firstKey()).isEqualTo(MONDAY.minusWeeks(11));
            assertThat(grass.intensityOn(MONDAY)).isEqualTo(4);
            assertThat(grass.intensityOn(MONDAY.plusDays(1))).isEqualTo(1);
            assertThat(grass.intensityOn(MONDAY.plusDays(2))).isZero();
        }
    }

    @Nested
    @org.junit.jupiter.api.DisplayName("WeeklyGoal")
    class WeeklyGoalTest {
        @ParameterizedTest(name = "횟수 {0}, 거리 {1}m → 거부")
        @CsvSource({"0,5000", "8,5000", "3,0"})
        void rejectsOutOfRange(int count, long meters) {
            assertThatThrownBy(
                            () -> WeeklyGoal.of(new Week(MONDAY), count, Distance.ofMeters(meters)))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((BusinessRuleException) e).reason())
                    .isEqualTo(ReasonCode.GOAL_INVALID);
        }

        @Test
        @org.junit.jupiter.api.DisplayName("달성률은 100%를 넘어도 실제 값 그대로(FR-056)")
        void achievementCanExceed100() {
            WeeklyGoal goal = WeeklyGoal.of(new Week(MONDAY), 2, Distance.ofKm(10));
            WeeklyAchievement a = goal.achievement(3, Distance.ofKm(15));
            assertThat(a.countPercent()).isEqualTo(150);
            assertThat(a.distancePercent()).isEqualTo(150);
            assertThat(goal.achievement(1, Distance.ofKm(2.5)).distancePercent()).isEqualTo(25);
        }
    }

    @Nested
    @org.junit.jupiter.api.DisplayName("RunningGoal")
    class RunningGoalTest {
        private final RunningGoal goals = RunningGoal.create(1);

        @Test
        @org.junit.jupiter.api.DisplayName("지난 날짜 계획은 PAST_DATE, 같은 날짜는 바꾼다(FR-073)")
        void putPlan_rejectsPastAndReplacesSameDate() {
            LocalDate today = MONDAY.plusDays(2);
            assertThatThrownBy(
                            () ->
                                    goals.putPlan(
                                            new DailyPlan(
                                                    today.minusDays(1), Distance.ofKm(3), null),
                                            today))
                    .extracting(e -> ((BusinessRuleException) e).reason())
                    .isEqualTo(ReasonCode.PAST_DATE);
            goals.putPlan(new DailyPlan(today, Distance.ofKm(3), null), today);
            goals.putPlan(new DailyPlan(today, Distance.ofKm(5), new Pace(330)), today);
            assertThat(goals.planOn(today).orElseThrow().distance()).isEqualTo(Distance.ofKm(5));
            assertThat(goals.planOn(today).orElseThrow().targetPace()).isEqualTo(new Pace(330));
            assertThatThrownBy(() -> new DailyPlan(today, Distance.ZERO, null))
                    .extracting(e -> ((BusinessRuleException) e).reason())
                    .isEqualTo(ReasonCode.PLAN_INVALID);
        }

        @Test
        @org.junit.jupiter.api.DisplayName("계획 취소: 그날 계획만 지우고, 없으면 그대로, 지난 날짜는 PAST_DATE")
        void deletePlan_removesOnlyThatDayAndIsIdempotent() {
            LocalDate today = MONDAY.plusDays(2);
            goals.putPlan(new DailyPlan(today, Distance.ofKm(3), null), today);
            goals.putPlan(new DailyPlan(today.plusDays(1), Distance.ofKm(5), null), today);

            goals.deletePlan(today, today);
            goals.deletePlan(today, today);

            assertThat(goals.planOn(today)).isEmpty();
            assertThat(goals.planOn(today.plusDays(1))).isPresent();
            assertThatThrownBy(() -> goals.deletePlan(today.minusDays(1), today))
                    .extracting(e -> ((BusinessRuleException) e).reason())
                    .isEqualTo(ReasonCode.PAST_DATE);
        }

        @Test
        @org.junit.jupiter.api.DisplayName("checkOn: 부족 = max(0, 계획 − 실제)(FR-074)")
        void checkOn_shortfall() {
            goals.putPlan(new DailyPlan(MONDAY, Distance.ofKm(5), null), MONDAY);
            PlanCheck partial =
                    goals.checkOn(MONDAY, new DayActual(Distance.ofKm(3), new Pace(360)))
                            .orElseThrow();
            assertThat(partial.shortfall()).isEqualTo(Distance.ofKm(2));
            assertThat(partial.fulfilled()).isFalse();
            PlanCheck over =
                    goals.checkOn(MONDAY, new DayActual(Distance.ofKm(6), null)).orElseThrow();
            assertThat(over.shortfall()).isEqualTo(Distance.ZERO);
            assertThat(over.fulfilled()).isTrue();
            assertThat(goals.checkOn(MONDAY.plusDays(1), DayActual.NONE)).isEmpty();
        }

        @Test
        @org.junit.jupiter.api.DisplayName(
                "supplementFor: 오늘 계획 + 이번 주 앞 부족 합을 1km 올림, 상한 cap(A-20, FR-187)")
        void supplementFor_roundsUpAndCaps() {
            LocalDate wednesday = MONDAY.plusDays(2);
            goals.putPlan(new DailyPlan(MONDAY, Distance.ofKm(5), null), MONDAY);
            goals.putPlan(new DailyPlan(MONDAY.plusDays(1), Distance.ofKm(3), null), MONDAY);
            goals.putPlan(new DailyPlan(wednesday, Distance.ofKm(4), null), MONDAY);
            Map<LocalDate, Distance> actuals =
                    Map.of(MONDAY, Distance.ofMeters(4_700), MONDAY.plusDays(1), Distance.ofKm(5));
            // 부족 300m(월) + 0(화) + 오늘 4km = 4.3km → 5km
            assertThat(goals.supplementFor(wednesday, actuals, Distance.ofKm(10)))
                    .isEqualTo(Distance.ofKm(5));
            assertThat(goals.supplementFor(wednesday, Map.of(), Distance.ofKm(10)))
                    .isEqualTo(Distance.ofKm(10));
            assertThat(goals.supplementFor(wednesday, Map.of(), Distance.ofKm(8)))
                    .isEqualTo(Distance.ofKm(8));
            assertThat(goals.supplementFor(MONDAY.plusDays(3), actuals, Distance.ofKm(10)))
                    .isEqualTo(Distance.ZERO);
        }

        @Test
        @org.junit.jupiter.api.DisplayName("주간 목표는 같은 주면 바꾸고 plannedDays는 기간 안 계획일 오름차순")
        void weeklyGoalAndPlannedDays() {
            Week week = new Week(MONDAY);
            goals.setWeeklyGoal(WeeklyGoal.of(week, 3, Distance.ofKm(15)));
            goals.setWeeklyGoal(WeeklyGoal.of(week, 4, Distance.ofKm(20)));
            assertThat(goals.goalOf(week).orElseThrow().count()).isEqualTo(4);
            assertThat(goals.goalOf(week.previous())).isEmpty();
            goals.putPlan(new DailyPlan(MONDAY.plusDays(3), Distance.ofKm(3), null), MONDAY);
            goals.putPlan(new DailyPlan(MONDAY.plusDays(1), Distance.ofKm(3), null), MONDAY);
            assertThat(goals.plannedDays(new DateRange(MONDAY, MONDAY.plusDays(6))))
                    .containsExactly(MONDAY.plusDays(1), MONDAY.plusDays(3));
        }
    }

    @Nested
    @org.junit.jupiter.api.DisplayName("FitnessSummary.from")
    class FitnessSummaryTest {
        @Test
        @org.junit.jupiter.api.DisplayName("직전이 없으면 변화량 0, 순번은 numbered로 맞춘다")
        void firstReport_hasNoChange() {
            List<RunSummary> recent =
                    List.of(
                            run(1, NOW.withHour(7), 5_000, 1_500),
                            run(1, NOW.minusDays(1).withHour(7), 5_000, 1_500));
            FitnessSummary s = FitnessSummary.from(recent, List.of()).numbered(2);
            assertThat(s.fromRun()).isEqualTo(1);
            assertThat(s.toRun()).isEqualTo(2);
            assertThat(s.runCount()).isEqualTo(2);
            assertThat(s.distanceChangeMeters()).isZero();
            assertThat(s.paceChangeSeconds()).isZero();
            assertThat(s.frequentDay()).isEqualTo(DayOfWeek.WEDNESDAY);
            assertThat(s.frequentHourBand()).isEqualTo(2);
            assertThat(s.distanceTrend()).isEqualTo(Trend.STEADY);
        }

        @Test
        @org.junit.jupiter.api.DisplayName("최근 10회와 직전 10회의 평균 거리·페이스를 비교하고 요일·시간대는 최빈값")
        void comparesWithPrevious() {
            List<RunSummary> recent = new ArrayList<>();
            List<RunSummary> previous = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                recent.add(run(1, NOW.minusDays(i).withHour(19), 6_000, 1_680));
                previous.add(run(1, NOW.minusDays(20 + i).withHour(7), 5_000, 1_500));
            }
            FitnessSummary s = FitnessSummary.from(recent, previous).numbered(20);
            assertThat(s.fromRun()).isEqualTo(11);
            assertThat(s.distanceChangeMeters()).isEqualTo(1_000);
            assertThat(s.paceChangeSeconds()).isEqualTo(-20);
            assertThat(s.frequentHourBand()).isEqualTo(6);
            assertThat(s.distanceTrend()).isEqualTo(Trend.IMPROVING);
            assertThat(s.paceTrend()).isEqualTo(Trend.IMPROVING);
            assertThat(s.averageScoreValue()).isPositive();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("최근 기록이 없거나 10회를 넘으면 IllegalArgumentException")
        void rejectsBadWindow() {
            assertThatThrownBy(() -> FitnessSummary.from(List.of(), List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            List<RunSummary> eleven = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                eleven.add(run(1, NOW.minusDays(i), 5_000, 1_500));
            }
            assertThatThrownBy(() -> FitnessSummary.from(eleven, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @org.junit.jupiter.api.DisplayName("Report")
    class ReportTest {
        @Test
        @org.junit.jupiter.api.DisplayName("같은 요약 순번을 다시 받으면 코멘트를 지우지 않는다(T-16), 새 순번이면 비운다")
        void refresh_keepsCommentForSameRun() {
            Report report = Report.create(1, summary(5, 0, 0), NOW);
            assertThat(report.view().status()).isEqualTo(ReportView.Status.COMMENT_PREPARING);
            assertThat(report.view().message()).isEqualTo("코멘트 준비 중");
            assertThat(report.attachComment(new Comment("좋아요", CommentSource.AI, 5))).isTrue();
            assertThat(report.refresh(summary(5, 0, 0), NOW)).isFalse();
            assertThat(report.refresh(summary(4, 0, 0), NOW)).isFalse();
            assertThat(report.hasComment()).isTrue();
            assertThat(report.view().commentSource()).isEqualTo(CommentSource.AI);
            assertThat(report.refresh(summary(6, 0, 0), NOW)).isTrue();
            assertThat(report.hasComment()).isFalse();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("요약이 바뀐 뒤 늦게 온 코멘트는 버린다")
        void attachComment_dropsStaleComment() {
            Report report = Report.create(1, summary(6, 0, 0), NOW);
            assertThat(report.attachComment(new Comment("늦음", CommentSource.RULE, 5))).isFalse();
            assertThat(report.hasComment()).isFalse();
            assertThat(ReportView.none().message()).isEqualTo("유효 러닝 기록 없음");
        }
    }

    @Nested
    @org.junit.jupiter.api.DisplayName("RankingSnapshot (A-19)")
    class RankingSnapshotTest {
        private RankScore score(long id, long meters) {
            return new RankScore(
                    id, DisplayName.of("러너" + id), Tier.GOLD, Distance.ofMeters(meters));
        }

        @Test
        @org.junit.jupiter.api.DisplayName("같은 거리는 같은 순위, 다음 순위는 건너뛴다(1, 2, 2, 4)")
        void build_sharesRanksAndSkips() {
            RankingSnapshot s =
                    RankingSnapshot.build(
                            RankingKind.PERSONAL,
                            AggregationPeriod.WEEKLY,
                            NOW,
                            List.of(
                                    score(4, 1_000),
                                    score(3, 5_000),
                                    score(2, 5_000),
                                    score(1, 9_000)));
            assertThat(s.entries()).extracting(RankEntry::rank).containsExactly(1, 2, 2, 4);
            assertThat(s.entries())
                    .extracting(RankEntry::subjectId)
                    .containsExactly(1L, 2L, 3L, 4L);
            assertThat(s.entryOf(3)).get().extracting(RankEntry::rank).isEqualTo(2);
            assertThat(s.entryOf(99)).isEmpty();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("gapToNext: 바로 위 거리까지 남은 거리, 1위·없는 대상은 비어 있다")
        void gapToNext() {
            RankingSnapshot s =
                    RankingSnapshot.build(
                            RankingKind.CREW,
                            AggregationPeriod.MONTHLY,
                            NOW,
                            List.of(
                                    score(1, 9_000),
                                    score(2, 5_000),
                                    score(3, 5_000),
                                    score(4, 1_000)));
            assertThat(s.gapToNext(4)).contains(Distance.ofMeters(4_000));
            assertThat(s.gapToNext(2)).contains(Distance.ofMeters(4_000));
            assertThat(s.gapToNext(1)).isEmpty();
            assertThat(s.gapToNext(99)).isEmpty();
            assertThat(
                            RankingSnapshot.build(
                                            RankingKind.CREW,
                                            AggregationPeriod.MONTHLY,
                                            NOW,
                                            List.of())
                                    .entries())
                    .isEmpty();
        }
    }

    @Nested
    @org.junit.jupiter.api.DisplayName("규칙 문구")
    class RuleCommentTest {
        private final RuleCommentCatalog catalog = new RuleCommentCatalog();

        @Test
        @org.junit.jupiter.api.DisplayName("문구는 20개이고 페이스 → 거리 → 빈도 순으로 정체가 아닌 지표를 고른다(FR-066)")
        void pickByFirstNonSteadyMetric() {
            assertThat(catalog.size()).isEqualTo(20);
            String paceUp = catalog.pick(summary(10, 0, -30));
            String distanceDown = catalog.pick(summary(10, -1_000, 0));
            String steady = catalog.pick(summary(10, 0, 0));
            assertThat(paceUp).contains("페이스");
            assertThat(distanceDown).contains("거리");
            assertThat(steady).isNotBlank();
            assertThat(catalog.pick(summary(10, 0, -30))).isEqualTo(paceUp);
            assertThat(new RuleCommentGenerator(catalog).generate(summary(10, 0, 0), null))
                    .get()
                    .extracting(Comment::source, Comment::forRun)
                    .containsExactly(CommentSource.RULE, 10);
        }
    }
}
