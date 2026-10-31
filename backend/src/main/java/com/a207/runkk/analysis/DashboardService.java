package com.a207.runkk.analysis;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Week;
import com.a207.runkk.crew.CrewMembershipReader;
import com.a207.runkk.crew.CrewSummary;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.policy.LandmarkTable;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.reward.RewardService;
import com.a207.runkk.running.DayStats;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunSummary;
import com.a207.runkk.running.TotalStats;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드를 조립한다(설계 6.1.4 DashboardService, srs.md 6.3 3.1). 기록은 RunQueryService, 크루는
 * CrewMembershipReader로만 읽고 WeeklyRace 테이블을 읽지 않는다(D-32). 크루원 개인 기록은 드러내지 않는다(FR-054, FR-055).
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    /** 크루 누적 거리를 셀 때 시작일(전체 기간). */
    private static final LocalDate ALL_TIME_START = LocalDate.EPOCH;

    /** 연속이 잔디 범위 밖으로 이어질 때 한 번에 더 읽는 주 수(D136). */
    private static final int STREAK_LOOKBACK_WEEKS = 52;

    private final RunQueryService runQueryService;
    private final RunningGoalRepository runningGoalRepository;
    private final CrewMembershipReader crewMembershipReader;
    private final MemberQueryService memberQueryService;
    private final OperationPolicy policy;
    private final RewardService rewardService;
    private final Clock clock;

    public DashboardService(
            RunQueryService runQueryService,
            RunningGoalRepository runningGoalRepository,
            CrewMembershipReader crewMembershipReader,
            MemberQueryService memberQueryService,
            OperationPolicy policy,
            RewardService rewardService,
            Clock clock) {
        this.runQueryService = runQueryService;
        this.runningGoalRepository = runningGoalRepository;
        this.crewMembershipReader = crewMembershipReader;
        this.memberQueryService = memberQueryService;
        this.policy = policy;
        this.rewardService = rewardService;
        this.clock = clock;
    }

    /** baseDate가 null이면 오늘. */
    public DashboardView dashboard(long memberId, LocalDate baseDate) {
        LocalDate base = baseDate == null ? LocalDate.now(clock) : baseDate;
        DateRange grassRange = Grass.rangeEndingOn(base);
        Map<LocalDate, DayStats> daily = runQueryService.dailyStats(memberId, grassRange);
        Map<LocalDate, Distance> dailyDistance = distancesOf(daily);
        TotalStats totals = runQueryService.totals(memberId);
        LandmarkTable landmarks = policy.landmarkTable();
        DateRange month = monthOf(base);
        List<RunSummary> monthRuns = runQueryService.summariesBetween(memberId, month);
        Set<LocalDate> runDays =
                untilBroken(
                        runDays(dailyDistance),
                        grassRange.start(),
                        base,
                        range -> distancesOf(runQueryService.dailyStats(memberId, range)),
                        true);
        return new DashboardView(
                base,
                memberQueryService.preferenceOf(memberId).unit(),
                todayOf(monthRuns, base),
                new DashboardView.Totals(totals.distance(), totals.elapsed(), totals.count()),
                landmarks.personalProgressOf(totals.distance()).label(),
                Streak.countBack(runDays, base),
                Grass.of(dailyDistance, grassRange),
                weeklyOf(memberId, base, daily),
                crewAreaOf(memberId, base, landmarks),
                calendarOf(monthRuns),
                rewardService.balanceOf(memberId),
                (int) monthRuns.stream().filter(RunSummary::valid).count(),
                Distance.ofMeters(
                        monthRuns.stream()
                                .filter(RunSummary::valid)
                                .mapToLong(r -> r.distance().meters())
                                .sum()),
                Streak.countWeeksBack(runDays, base));
    }

    /** 오늘 달린 거리·시간·횟수. 유효 기록만 센다(집계 제외 기록은 달력·누적과 같이 뺀다, v0.10). */
    private static DashboardView.Totals todayOf(List<RunSummary> monthRuns, LocalDate base) {
        List<RunSummary> today =
                monthRuns.stream()
                        .filter(RunSummary::valid)
                        .filter(r -> r.startedAt().toLocalDate().equals(base))
                        .toList();
        return new DashboardView.Totals(
                Distance.ofMeters(today.stream().mapToLong(r -> r.distance().meters()).sum()),
                today.stream().map(RunSummary::elapsed).reduce(Duration.ZERO, Duration::plus),
                today.size());
    }

    private WeeklyAchievement weeklyOf(
            long memberId, LocalDate base, Map<LocalDate, DayStats> daily) {
        Week week = Week.of(base.atStartOfDay());
        return runningGoalRepository
                .findOrCreate(memberId)
                .goalOf(week)
                .map(
                        goal -> {
                            List<DayStats> days =
                                    daily.values().stream()
                                            .filter(d -> !d.date().isBefore(week.monday()))
                                            .filter(d -> !d.date().isAfter(base))
                                            .toList();
                            return goal.achievement(
                                    days.stream().mapToInt(DayStats::count).sum(),
                                    Distance.ofMeters(
                                            days.stream()
                                                    .mapToLong(d -> d.distance().meters())
                                                    .sum()));
                        })
                .orElse(null);
    }

    private DashboardView.CrewArea crewAreaOf(
            long memberId, LocalDate base, LandmarkTable landmarks) {
        return crewMembershipReader
                .activeCrewOf(memberId)
                .map(crew -> crewArea(crew, base, landmarks))
                .orElse(null);
    }

    private DashboardView.CrewArea crewArea(
            CrewSummary crew, LocalDate base, LandmarkTable landmarks) {
        List<Long> memberIds = crewMembershipReader.activeMemberIds(crew.crewId());
        DateRange grassRange = Grass.rangeEndingOn(base);
        Map<LocalDate, Distance> daily = runQueryService.dailyDistances(memberIds, grassRange);
        Distance total =
                runQueryService
                        .distancesByMember(memberIds, new DateRange(ALL_TIME_START, base))
                        .values()
                        .stream()
                        .reduce(Distance.ZERO, Distance::plus);
        return new DashboardView.CrewArea(
                crew.crewId(),
                crew.name(),
                Streak.countBack(
                        untilBroken(
                                runDays(daily),
                                grassRange.start(),
                                base,
                                range -> runQueryService.dailyDistances(memberIds, range),
                                false),
                        base),
                Grass.of(daily, grassRange),
                landmarks.crewProgressOf(total).label());
    }

    /**
     * 연속 일·주가 읽은 범위(처음은 잔디 12주) 첫날에 닿으면 그 앞 52주를 더 읽어 끊긴 곳까지 러닝 날짜를 모은다(D136, 상한 없음). 회원 한 명의 날짜별
     * 집계를 52주씩 읽으므로 1년 연속마다 한 번 더 읽는다. 연속이 끊기면 더 읽지 않는다.
     *
     * @param weekly 주 연속도 볼지(개인 대시보드). 크루는 일 연속만 쓴다
     */
    private static Set<LocalDate> untilBroken(
            Set<LocalDate> loaded,
            LocalDate loadedFrom,
            LocalDate base,
            Function<DateRange, Map<LocalDate, Distance>> reader,
            boolean weekly) {
        Set<LocalDate> days = new HashSet<>(loaded);
        LocalDate from = loadedFrom;
        while (reaches(Streak.firstDayBack(days, base), from)
                || (weekly && reaches(Streak.firstWeekBack(days, base), from))) {
            DateRange earlier =
                    new DateRange(from.minusWeeks(STREAK_LOOKBACK_WEEKS), from.minusDays(1));
            days.addAll(runDays(reader.apply(earlier)));
            from = earlier.start();
        }
        return days;
    }

    private static boolean reaches(Optional<LocalDate> streakStart, LocalDate loadedFrom) {
        return streakStart.filter(start -> !start.isAfter(loadedFrom)).isPresent();
    }

    private static Map<LocalDate, List<DashboardView.CalendarRun>> calendarOf(
            List<RunSummary> monthRuns) {
        Map<LocalDate, List<DashboardView.CalendarRun>> calendar = new TreeMap<>();
        monthRuns.stream()
                .sorted((a, b) -> a.startedAt().compareTo(b.startedAt()))
                .forEach(
                        r ->
                                calendar.computeIfAbsent(
                                                r.startedAt().toLocalDate(),
                                                day -> new ArrayList<>())
                                        .add(
                                                new DashboardView.CalendarRun(
                                                        r.recordId(), r.distance(), r.valid())));
        return calendar;
    }

    private static Map<LocalDate, Distance> distancesOf(Map<LocalDate, DayStats> daily) {
        Map<LocalDate, Distance> result = new LinkedHashMap<>();
        daily.forEach((day, stats) -> result.put(day, stats.distance()));
        return result;
    }

    private static Set<LocalDate> runDays(Map<LocalDate, Distance> daily) {
        return daily.entrySet().stream()
                .filter(e -> e.getValue().meters() > 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static DateRange monthOf(LocalDate base) {
        return new DateRange(base.withDayOfMonth(1), base.with(TemporalAdjusters.lastDayOfMonth()));
    }
}
