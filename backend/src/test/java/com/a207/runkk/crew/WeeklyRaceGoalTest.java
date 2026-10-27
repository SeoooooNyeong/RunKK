package com.a207.runkk.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.Week;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 2.1.10.13 WeeklyRace 공동 목표(FR-138, FR-140, D-105): TC-6139, TC-6149의 진행률. */
@DisplayName("WeeklyRace 공동 목표")
class WeeklyRaceGoalTest {

    private static final LocalDateTime N = LocalDateTime.of(2026, 10, 22, 19, 0);

    private static WeeklyRace race(long totalMeters) {
        WeeklyRace race = WeeklyRace.empty(7, Week.of(N));
        if (totalMeters > 0) {
            race.add(3, "r-1", Distance.ofMeters(totalMeters));
        }
        return race;
    }

    @Test
    @DisplayName("TC-6139: 999m는 GOAL_INVALID(목표 그대로), 1,000m·1,001m는 받는다")
    void minGoal() {
        WeeklyRace race = race(0);
        assertThatThrownBy(() -> race.setGoal(Distance.ofMeters(999), 1))
                .satisfies(
                        e ->
                                assertThat(((BusinessRuleException) e).reason())
                                        .isEqualTo(ReasonCode.GOAL_INVALID));
        assertThat(race.goal()).isEmpty();
        race.setGoal(Distance.ofMeters(1_000), 1);
        assertThat(race.goal()).contains(Distance.ofMeters(1_000));
        race.setGoal(Distance.ofMeters(1_001), 2);
        assertThat(race.goal()).contains(Distance.ofMeters(1_001));
        assertThat(race.goalSetBy()).contains(2L);
    }

    @Test
    @DisplayName("TC-6149: 진행률은 버림이고 100에서 자른다, 목표가 없으면 없음")
    void progress() {
        long[][] cases = {{2_500, 25}, {9_999, 99}, {10_000, 100}, {15_000, 100}};
        for (long[] c : cases) {
            WeeklyRace race = race(c[0]);
            race.setGoal(Distance.ofMeters(10_000), 1);
            assertThat(race.goalProgressPercent()).as(c[0] + "m").contains((int) c[1]);
        }
        assertThat(race(5_000).goalProgressPercent()).isEmpty();
    }

    @Test
    @DisplayName("이미 달성한 주에 목표를 바꿔도 달성 일시는 그대로다(TC-6141 ②)")
    void achievedStays() {
        WeeklyRace race =
                WeeklyRace.restore(
                        7, Week.of(N), new WeeklyRace.GoalState(Distance.ofMeters(7_000), 1L, N));
        race.setGoal(Distance.ofMeters(5_000), 1);
        assertThat(race.isAchieved()).isTrue();
        assertThat(race.achievedAt()).contains(N);
    }
}
