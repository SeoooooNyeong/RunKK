package com.a207.runkk.analysis;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.Week;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.running.DayStats;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주간 목표·날짜별 계획·이행 점검·미이행 알림(설계 6.1.4 CoachService, FR-042, FR-072~FR-075, FR-187, design-review.md
 * M29).
 */
@Service
public class CoachService {

    /** 계획·주간 목표 보존 기간(설계에 없어 정한 값, B 단계 결정). */
    static final java.time.Period PLAN_RETENTION = java.time.Period.ofYears(1);

    /** 이행 현황 한 번에 볼 수 있는 최대 기간(일). */
    static final long MAX_PROGRESS_DAYS = 93;

    static final String PLAN_MISSED_TEXT = "오늘 계획한 러닝이 아직 남아 있어요";

    private static final Logger log = LoggerFactory.getLogger(CoachService.class);

    private final RunningGoalRepository runningGoalRepository;
    private final RunQueryService runQueryService;
    private final NotificationService notificationService;
    private final OperationPolicy policy;
    private final Clock clock;

    public CoachService(
            RunningGoalRepository runningGoalRepository,
            RunQueryService runQueryService,
            NotificationService notificationService,
            OperationPolicy policy,
            Clock clock) {
        this.runningGoalRepository = runningGoalRepository;
        this.runQueryService = runQueryService;
        this.notificationService = notificationService;
        this.policy = policy;
        this.clock = clock;
    }

    /** 이번 주 목표. 범위를 어기면 GOAL_INVALID. */
    @Transactional
    public GoalResult setWeeklyGoal(long memberId, WeeklyGoalCommand cmd) {
        WeeklyGoal goal =
                WeeklyGoal.of(Week.of(LocalDateTime.now(clock)), cmd.count(), cmd.distance());
        RunningGoal goals = runningGoalRepository.findOrCreate(memberId);
        goals.setWeeklyGoal(goal);
        runningGoalRepository.save(goals);
        return GoalResult.of(goal);
    }

    /** 오늘보다 이전 날짜면 PAST_DATE. */
    @Transactional
    public GoalResult putPlan(long memberId, DailyPlan plan) {
        RunningGoal goals = runningGoalRepository.findOrCreate(memberId);
        goals.putPlan(plan, LocalDate.now(clock));
        runningGoalRepository.save(goals);
        return GoalResult.of(plan);
    }

    /** 그날 계획을 취소한다. 계획이 없어도 오류가 아니다. 오늘보다 이전 날짜면 PAST_DATE. */
    @Transactional
    public void deletePlan(long memberId, LocalDate date) {
        RunningGoal goals = runningGoalRepository.findOrCreate(memberId);
        goals.deletePlan(date, LocalDate.now(clock));
        runningGoalRepository.save(goals);
    }

    /** 날짜마다 checkOn, 오늘 줄에는 supplementFor(cap = policy.supplementCap()). 기간은 93일까지. */
    @Transactional(readOnly = true)
    public PlanProgressView progress(long memberId, DateRange range) {
        if (ChronoUnit.DAYS.between(range.start(), range.end()) >= MAX_PROGRESS_DAYS) {
            throw new IllegalArgumentException("조회 기간은 93일까지");
        }
        LocalDate today = LocalDate.now(clock);
        RunningGoal goals = runningGoalRepository.findOrCreate(memberId);
        Map<LocalDate, DayStats> daily =
                runQueryService.dailyStats(memberId, coverWeekOf(range, today));
        Map<LocalDate, Distance> actuals = distancesOf(daily);
        Distance cap = policy.supplementCap().cap();
        List<PlanProgressView.Day> days = new ArrayList<>();
        range.days()
                .forEach(
                        day -> {
                            DayActual actual = actualOf(daily.get(day));
                            Distance supplement =
                                    day.equals(today) && goals.planOn(day).isPresent()
                                            ? goals.supplementFor(today, actuals, cap)
                                            : null;
                            days.add(dayOf(goals, day, actual, supplement));
                        });
        return new PlanProgressView(days);
    }

    /** 오늘 계획과 보충 추천 거리(워치 시작 화면·코치 계획 따르기). */
    @Transactional(readOnly = true)
    public TodayPlanView todayPlan(long memberId) {
        LocalDate today = LocalDate.now(clock);
        RunningGoal goals = runningGoalRepository.findOrCreate(memberId);
        DateRange week = new DateRange(Week.of(today.atStartOfDay()).monday(), today);
        Map<LocalDate, Distance> actuals = distancesOf(runQueryService.dailyStats(memberId, week));
        Distance supplement = goals.supplementFor(today, actuals, policy.supplementCap().cap());
        return goals.planOn(today)
                .map(
                        plan ->
                                new TodayPlanView(
                                        today, plan.distance(), plan.targetPace(), supplement))
                .orElseGet(() -> new TodayPlanView(today, null, null, Distance.ZERO));
    }

    /**
     * 3시. batch.analysis의 PlanPurgeJob이 부른다. 보존 기간(1년)보다 오래된 날짜별 계획·주간 목표 행을 지운다(B5 리뷰: 지난 행이 끝없이
     * 쌓임). 성장·코치 화면은 최근 기간만 읽는다. 지운 행 수를 돌려준다.
     */
    @Transactional
    public int purgePastPlans(LocalDate today) {
        return runningGoalRepository.deletePlansAndGoalsBefore(today.minus(PLAN_RETENTION));
    }

    /**
     * 20시. batch.analysis의 PlanReminderJob이 부른다. 오늘 계획이 있는데 오늘 유효 기록이 0인 회원마다 PLAN_MISSED(FR-075).
     * 수신 설정은 NotificationService가 본다. 보낸 수를 돌려준다. 한 회원의 실패는 로그만 남긴다.
     */
    public int remindMissedPlans(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        List<Long> planned = runningGoalRepository.membersWithPlanOn(today);
        if (planned.isEmpty()) {
            return 0;
        }
        Map<Long, Distance> done =
                runQueryService.distancesByMember(planned, new DateRange(today, today));
        int sent = 0;
        for (Long memberId : planned) {
            if (done.getOrDefault(memberId, Distance.ZERO).meters() > 0) {
                continue;
            }
            try {
                notificationService.notify(
                        new NotificationRequest(
                                memberId, NotificationType.PLAN_MISSED, PLAN_MISSED_TEXT, null));
                sent++;
            } catch (RuntimeException e) {
                log.warn(
                        "plan_reminder_failed memberId={} error={}",
                        memberId,
                        e.getClass().getSimpleName());
            }
        }
        return sent;
    }

    private static PlanProgressView.Day dayOf(
            RunningGoal goals, LocalDate day, DayActual actual, Distance supplement) {
        return goals.checkOn(day, actual)
                .map(
                        check ->
                                new PlanProgressView.Day(
                                        day,
                                        check.plan().distance(),
                                        check.plan().targetPace(),
                                        check.actual(),
                                        check.actualPace(),
                                        check.shortfall(),
                                        supplement))
                .orElseGet(
                        () ->
                                new PlanProgressView.Day(
                                        day,
                                        null,
                                        null,
                                        actual.distance(),
                                        actual.pace(),
                                        null,
                                        null));
    }

    /** 오늘이 기간 안이면 이번 주 월요일부터 읽어 보충 추천의 앞 부족분을 셀 수 있게 한다. */
    private static DateRange coverWeekOf(DateRange range, LocalDate today) {
        if (!range.contains(today)) {
            return range;
        }
        LocalDate monday = Week.of(today.atStartOfDay()).monday();
        return new DateRange(monday.isBefore(range.start()) ? monday : range.start(), range.end());
    }

    private static DayActual actualOf(DayStats stats) {
        if (stats == null || stats.distance().meters() == 0) {
            return DayActual.NONE;
        }
        return new DayActual(stats.distance(), Pace.of(stats.elapsed(), stats.distance()));
    }

    private static Map<LocalDate, Distance> distancesOf(Map<LocalDate, DayStats> daily) {
        Map<LocalDate, Distance> result = new LinkedHashMap<>();
        daily.forEach((day, stats) -> result.put(day, stats.distance()));
        return result;
    }
}
