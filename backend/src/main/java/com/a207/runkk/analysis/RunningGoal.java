package com.a207.runkk.analysis;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.Week;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 회원의 주간 목표와 날짜별 계획(설계 6.1.4 RunningGoal, FR-072~FR-074, FR-187). 주는 월요일 날짜로, 계획은 날짜로 저장한다. 같은 회원의
 * 동시 변경은 version 낙관적 잠금으로 늦은 쪽이 다시 시도한다.
 */
@Entity
@Table(name = "running_goal")
public class RunningGoal {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @ElementCollection
    @CollectionTable(name = "weekly_goal", joinColumns = @JoinColumn(name = "member_id"))
    @MapKeyColumn(name = "week_start")
    private Map<LocalDate, GoalCell> weeklyGoals = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "daily_plan", joinColumns = @JoinColumn(name = "member_id"))
    @MapKeyColumn(name = "plan_date")
    private Map<LocalDate, PlanCell> plans = new HashMap<>();

    /** null이면 아직 저장 전(새 행). */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected RunningGoal() {}

    private RunningGoal(long memberId) {
        this.memberId = memberId;
    }

    public static RunningGoal create(long memberId) {
        return new RunningGoal(memberId);
    }

    /** before보다 이전 날짜의 계획·이전 주 목표를 지우고 지운 수를 돌려준다(인메모리 저장소·정리 작업 시험용 같은 규칙). */
    public int dropBefore(LocalDate before) {
        int count = plans.size() + weeklyGoals.size();
        plans.keySet().removeIf(d -> d.isBefore(before));
        weeklyGoals.keySet().removeIf(d -> d.isBefore(before));
        return count - plans.size() - weeklyGoals.size();
    }

    public long getMemberId() {
        return memberId;
    }

    /** 같은 주면 바꾼다. */
    public void setWeeklyGoal(WeeklyGoal goal) {
        weeklyGoals.put(goal.week().monday(), new GoalCell(goal.count(), goal.distance().meters()));
    }

    /** 오늘보다 이전 날짜면 PAST_DATE. 같은 날짜면 바꾼다(FR-073). */
    public void putPlan(DailyPlan plan, LocalDate today) {
        if (plan.date().isBefore(today)) {
            throw new BusinessRuleException(ReasonCode.PAST_DATE);
        }
        Integer pace = plan.targetPace() == null ? null : plan.targetPace().secondsPerKm();
        plans.put(plan.date(), new PlanCell(plan.distance().meters(), pace));
    }

    /** 그날 계획을 지운다(취소). 없으면 그대로 둔다. 오늘보다 이전 날짜면 PAST_DATE(putPlan과 같은 규칙). */
    public void deletePlan(LocalDate date, LocalDate today) {
        if (date.isBefore(today)) {
            throw new BusinessRuleException(ReasonCode.PAST_DATE);
        }
        plans.remove(date);
    }

    public Optional<DailyPlan> planOn(LocalDate day) {
        return Optional.ofNullable(plans.get(day)).map(cell -> cell.toPlan(day));
    }

    public Optional<WeeklyGoal> goalOf(Week week) {
        return Optional.ofNullable(weeklyGoals.get(week.monday()))
                .map(cell -> WeeklyGoal.of(week, cell.runCount(), cell.distance()));
    }

    /** 그날 계획이 있으면 부족 = max(0, 계획 − 실제)(FR-074). */
    public Optional<PlanCheck> checkOn(LocalDate day, DayActual actual) {
        return planOn(day)
                .map(
                        plan -> {
                            Distance shortfall = plan.distance().minus(actual.distance());
                            return new PlanCheck(
                                    plan,
                                    actual.distance(),
                                    actual.pace(),
                                    shortfall,
                                    shortfall.meters() == 0);
                        });
    }

    /**
     * 오늘 보충 추천 거리 = min(cap, 1km 단위 올림(오늘 계획 + 이번 주 오늘 전 부족 합))(FR-187, 6.2 A-20). 오늘 계획이 없으면 0.
     */
    public Distance supplementFor(LocalDate today, Map<LocalDate, Distance> actuals, Distance cap) {
        Optional<DailyPlan> todayPlan = planOn(today);
        if (todayPlan.isEmpty()) {
            return Distance.ZERO;
        }
        long shortfall = 0;
        for (LocalDate day = Week.of(today.atStartOfDay()).monday();
                day.isBefore(today);
                day = day.plusDays(1)) {
            Distance done = actuals.getOrDefault(day, Distance.ZERO);
            shortfall += planOn(day).map(p -> p.distance().minus(done).meters()).orElse(0L);
        }
        long wanted = ceilToKm(todayPlan.get().distance().meters() + shortfall);
        return Distance.ofMeters(Math.min(cap.meters(), wanted));
    }

    /** 기간 안 계획일(오름차순). */
    public List<LocalDate> plannedDays(DateRange range) {
        return plans.keySet().stream().filter(range::contains).sorted().toList();
    }

    private static long ceilToKm(long meters) {
        long km = Distance.METERS_PER_KM;
        return (meters + km - 1) / km * km;
    }

    /**
     * 주간 목표 저장 칸.
     *
     * @param runCount 목표 횟수
     * @param distanceMeters 목표 거리(미터)
     */
    @Embeddable
    record GoalCell(
            @Column(name = "run_count", nullable = false) int runCount,
            @Column(name = "distance_meters", nullable = false) long distanceMeters) {

        Distance distance() {
            return Distance.ofMeters(distanceMeters);
        }
    }

    /**
     * 날짜별 계획 저장 칸.
     *
     * @param distanceMeters 계획 거리(미터)
     * @param paceSecondsPerKm 목표 페이스(km당 초), 없으면 null
     */
    @Embeddable
    record PlanCell(
            @Column(name = "distance_meters", nullable = false) long distanceMeters,
            @Column(name = "pace_seconds_per_km") Integer paceSecondsPerKm) {

        DailyPlan toPlan(LocalDate day) {
            return new DailyPlan(
                    day,
                    Distance.ofMeters(distanceMeters),
                    paceSecondsPerKm == null ? null : new Pace(paceSecondsPerKm));
        }
    }
}
