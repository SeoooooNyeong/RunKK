package com.a207.runkk.analysis;

import static com.a207.runkk.analysis.AnalysisFixtures.SEOUL;
import static com.a207.runkk.analysis.AnalysisFixtures.run;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.DistanceUnit;
import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.RecordVisibility;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.crew.CrewMembershipReader;
import com.a207.runkk.crew.CrewSummary;
import com.a207.runkk.crew.WeeklyRaceService;
import com.a207.runkk.member.Gender;
import com.a207.runkk.member.MemberPreference;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.member.ProfileVisibility;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.policy.CalorieCoefficient;
import com.a207.runkk.policy.FeatureToggles;
import com.a207.runkk.policy.Landmark;
import com.a207.runkk.policy.LandmarkStage;
import com.a207.runkk.policy.LandmarkTable;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.SupplementCap;
import com.a207.runkk.reward.AccrualReason;
import com.a207.runkk.reward.RewardService;
import com.a207.runkk.running.BestEntry;
import com.a207.runkk.running.DayStats;
import com.a207.runkk.running.HeartRateSummary;
import com.a207.runkk.running.HeartRateZone;
import com.a207.runkk.running.NewRecordMark;
import com.a207.runkk.running.PauseInterval;
import com.a207.runkk.running.PeriodStats;
import com.a207.runkk.running.RunDetail;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunRecordSaved;
import com.a207.runkk.running.RunSummary;
import com.a207.runkk.running.SkyCondition;
import com.a207.runkk.running.TotalStats;
import com.a207.runkk.running.UnstableSegment;
import com.a207.runkk.running.Weather;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/** analysis 응용 서비스(14장 단위: 인메모리 저장소·가짜 조회 서비스·고정 시계). */
@org.junit.jupiter.api.DisplayName("analysis 응용 서비스")
class AnalysisServicesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 9, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final long ME = 1;
    private static final long OTHER = 2;

    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
    private final RunQueryService runs = mock(RunQueryService.class);
    private final MemberQueryService members = mock(MemberQueryService.class);
    private final CrewMembershipReader crews = mock(CrewMembershipReader.class);
    private final WeeklyRaceService races = mock(WeeklyRaceService.class);
    private final RewardService rewards = mock(RewardService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final OperationPolicy policy = mock(OperationPolicy.class);
    private final AnalysisFixtures.Goals goals = new AnalysisFixtures.Goals();
    private final AnalysisFixtures.Reports reports = new AnalysisFixtures.Reports();
    private final AnalysisFixtures.Snapshots snapshots = new AnalysisFixtures.Snapshots();
    private final AnalysisTransactions tx =
            new AnalysisTransactions(mock(PlatformTransactionManager.class));

    @BeforeEach
    void defaults() {
        when(policy.landmarkTable())
                .thenReturn(
                        new LandmarkTable(
                                List.of(
                                        new Landmark(
                                                "INCHEON",
                                                LandmarkStage.DOMESTIC,
                                                "인천",
                                                Distance.ofKm(28),
                                                true),
                                        new Landmark(
                                                "MOON",
                                                LandmarkStage.CREW_TOTAL,
                                                "달",
                                                Distance.ofKm(380_000),
                                                false))));
        when(policy.calorieCoefficient()).thenReturn(new CalorieCoefficient(65));
        when(policy.supplementCap()).thenReturn(new SupplementCap(Distance.ofKm(10)));
        when(policy.features()).thenReturn(new FeatureToggles(true, true));
        preference(ME, RecordVisibility.PUBLIC, false);
        when(runs.dailyStats(anyLong(), any())).thenReturn(Map.of());
        when(runs.summariesBetween(anyLong(), any())).thenReturn(List.of());
        when(runs.totals(anyLong())).thenReturn(new TotalStats(Distance.ZERO, Duration.ZERO, 0));
    }

    private void preference(long id, RecordVisibility visibility, boolean courseMapShared) {
        when(members.preferenceOf(id))
                .thenReturn(
                        new MemberPreference(
                                DistanceUnit.KM,
                                visibility,
                                courseMapShared,
                                ProfileVisibility.PUBLIC,
                                true));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("대시보드: 오늘·누적·랜드마크·스트릭·주간 달성률·크루 영역·달력(srs.md 6.3 3.1)")
    void dashboard() {
        DashboardService service =
                new DashboardService(runs, goals, crews, members, policy, rewards, clock);
        RunSummary today = run(ME, NOW.withHour(7), 5_000, 1_500);
        when(runs.summariesBetween(eq(ME), any())).thenReturn(List.of(today));
        when(runs.dailyStats(eq(ME), any()))
                .thenReturn(
                        Map.of(
                                TODAY,
                                new DayStats(TODAY, Distance.ofKm(5), Duration.ofMinutes(25), 1),
                                TODAY.minusDays(1),
                                new DayStats(
                                        TODAY.minusDays(1),
                                        Distance.ofKm(3),
                                        Duration.ofMinutes(18),
                                        1)));
        when(runs.totals(ME)).thenReturn(new TotalStats(Distance.ofKm(14), Duration.ofHours(2), 4));
        RunningGoal goal = RunningGoal.create(ME);
        goal.setWeeklyGoal(WeeklyGoal.of(com.a207.runkk.common.Week.of(NOW), 4, Distance.ofKm(16)));
        goals.save(goal);
        when(crews.activeCrewOf(ME)).thenReturn(Optional.of(new CrewSummary(7, "새벽크루", null)));
        when(crews.activeMemberIds(7)).thenReturn(List.of(ME, OTHER));
        when(runs.dailyDistances(any(), any())).thenReturn(Map.of(TODAY, Distance.ofKm(8)));
        when(runs.distancesByMember(any(), any()))
                .thenReturn(Map.of(ME, Distance.ofKm(14), OTHER, Distance.ofKm(20)));

        DashboardView view = service.dashboard(ME, null);

        assertThat(view.baseDate()).isEqualTo(TODAY);
        assertThat(view.today().count()).isEqualTo(1);
        assertThat(view.total().distance()).isEqualTo(Distance.ofKm(14));
        assertThat(view.landmark()).contains("인천").contains("50%");
        assertThat(view.streakDays()).isEqualTo(2);
        assertThat(view.streakWeeks()).as("어제·오늘은 같은 주(10/19 월요일 시작)").isEqualTo(1);
        assertThat(view.weekly().actualCount()).isEqualTo(2);
        assertThat(view.weekly().distancePercent()).isEqualTo(50);
        assertThat(view.crew().crewName()).isEqualTo("새벽크루");
        assertThat(view.crew().streakDays()).isEqualTo(1);
        assertThat(view.crew().landmark()).contains("인천");
        assertThat(view.calendar().get(TODAY))
                .extracting(DashboardView.CalendarRun::recordId)
                .containsExactly(today.recordId());

        when(crews.activeCrewOf(ME)).thenReturn(Optional.empty());
        goals.deleteByMember(ME);
        DashboardView alone = service.dashboard(ME, TODAY);
        assertThat(alone.crew()).isNull();
        assertThat(alone.weekly()).isNull();
    }

    /** 기록 저장소처럼 요청 범위 안 러닝 날짜만 돌려준다. 요청한 범위를 모은다. */
    private List<DateRange> storedRunDays(Set<LocalDate> days) {
        List<DateRange> asked = new ArrayList<>();
        when(runs.dailyStats(eq(ME), any()))
                .thenAnswer(
                        inv -> {
                            DateRange range = inv.getArgument(1);
                            asked.add(range);
                            Map<LocalDate, DayStats> result = new java.util.HashMap<>();
                            days.stream()
                                    .filter(range::contains)
                                    .forEach(
                                            d ->
                                                    result.put(
                                                            d,
                                                            new DayStats(
                                                                    d,
                                                                    Distance.ofKm(3),
                                                                    Duration.ofMinutes(18),
                                                                    1)));
                            return result;
                        });
        return asked;
    }

    /** 이번 주(TODAY가 속한 주)부터 거꾸로 weeks주 동안 매주 월요일 하루씩. skipThisWeek면 지난주부터. */
    private static Set<LocalDate> mondays(int weeks, boolean skipThisWeek) {
        LocalDate monday = TODAY.with(java.time.DayOfWeek.MONDAY);
        if (skipThisWeek) {
            monday = monday.minusWeeks(1);
        }
        Set<LocalDate> days = new java.util.HashSet<>();
        for (int i = 0; i < weeks; i++) {
            days.add(monday.minusWeeks(i));
        }
        return days;
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0}주 연속, 이번 주 기록 없음={1}")
    @org.junit.jupiter.params.provider.CsvSource({
        "11, false",
        "12, false",
        "13, false",
        "53, false",
        "130, false",
        "11, true",
        "12, true",
        "13, true",
        "53, true"
    })
    @org.junit.jupiter.api.DisplayName("대시보드 주 연속: 잔디 범위(12주)에 묶이지 않고 끊길 때까지 센다(D136, BE10-2)")
    void dashboard_streakWeeks_isNotCappedByGrassRange(int weeks, boolean skipThisWeek) {
        Set<LocalDate> days = mondays(weeks, skipThisWeek);
        // 연속 바로 앞 주는 비우고 그 앞에 기록을 하나 더 둔다(끊긴 뒤 기록은 세지 않는다)
        LocalDate oldest = days.stream().min(LocalDate::compareTo).orElseThrow();
        days.add(oldest.minusWeeks(2));
        List<DateRange> asked = storedRunDays(days);
        DashboardService service =
                new DashboardService(runs, goals, crews, members, policy, rewards, clock);

        DashboardView view = service.dashboard(ME, null);

        assertThat(view.streakWeeks()).isEqualTo(weeks);
        assertThat(view.grass().cells()).as("잔디는 그대로 12주(11주 + 이번 주 월~수)").hasSize(80);
        assertThat(asked).as("회원 한 명 조회는 잔디 1번 + 52주씩 몇 번").hasSizeLessThanOrEqualTo(4);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("대시보드 일 연속: 12주(84일)를 넘는 연속도 끊길 때까지 센다(D136)")
    void dashboard_streakDays_isNotCappedByGrassRange() {
        Set<LocalDate> days = new java.util.HashSet<>();
        for (int i = 0; i < 100; i++) {
            days.add(TODAY.minusDays(i));
        }
        days.add(TODAY.minusDays(110)); // 7/3(금), 그 다음 주(7/6~7/12)는 비어 주 연속도 끊긴다
        storedRunDays(days);
        DashboardService service =
                new DashboardService(runs, goals, crews, members, policy, rewards, clock);

        DashboardView view = service.dashboard(ME, null);

        assertThat(view.streakDays()).isEqualTo(100);
        assertThat(view.streakWeeks()).as("7/13 주 ~ 10/19 주").isEqualTo(15);
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0}일 연속, 오늘 기록={1}, 조회 {2}번")
    @org.junit.jupiter.params.provider.CsvSource({
        "79, true, 1",
        "80, true, 2",
        "81, true, 2",
        "100, true, 2",
        "443, true, 2",
        "444, true, 3",
        "445, true, 3",
        "78, false, 1",
        "79, false, 2",
        "443, false, 3"
    })
    @org.junit.jupiter.api.DisplayName(
            "대시보드 크루 일 연속: 잔디 범위(12주) 첫날에 닿으면 52주씩 더 읽어 끊긴 날까지 센다, 끊긴 날 앞 기록은 세지 않는다(D136, D-341,"
                    + " BE11-3)")
    void dashboard_crewStreakDays_isNotCappedByGrassRange(
            int streak, boolean ranToday, int expectedReads) {
        List<Long> crewMembers = List.of(ME, OTHER);
        when(crews.activeCrewOf(ME)).thenReturn(Optional.of(new CrewSummary(7, "새벽크루", null)));
        when(crews.activeMemberIds(7)).thenReturn(crewMembers);
        when(runs.distancesByMember(any(), any())).thenReturn(Map.of());
        LocalDate latest = ranToday ? TODAY : TODAY.minusDays(1);
        Set<LocalDate> days = new java.util.HashSet<>();
        for (int i = 0; i < streak; i++) {
            days.add(latest.minusDays(i));
        }
        LocalDate oldest = latest.minusDays(streak - 1L);
        days.add(oldest.minusDays(2)); // 하루 비우고 그 앞에 기록 하나
        List<DateRange> asked = new ArrayList<>();
        when(runs.dailyDistances(eq(crewMembers), any()))
                .thenAnswer(
                        inv -> {
                            DateRange range = inv.getArgument(1);
                            asked.add(range);
                            Map<LocalDate, Distance> result = new java.util.HashMap<>();
                            days.stream()
                                    .filter(range::contains)
                                    .forEach(d -> result.put(d, Distance.ofKm(5)));
                            return result;
                        });
        DashboardService service =
                new DashboardService(runs, goals, crews, members, policy, rewards, clock);

        DashboardView view = service.dashboard(ME, null);

        assertThat(view.crew().streakDays()).isEqualTo(streak);
        assertThat(view.crew().grass().cells()).as("크루 잔디는 그대로 12주").hasSize(80);
        assertThat(asked).as("잔디 1번 + 52주씩").hasSize(expectedReads);
        for (int i = 1; i < asked.size(); i++) {
            assertThat(asked.get(i).end())
                    .as("앞 범위 바로 전날까지 이어 읽는다")
                    .isEqualTo(asked.get(i - 1).start().minusDays(1));
            assertThat(asked.get(i).start()).isEqualTo(asked.get(i - 1).start().minusWeeks(52));
        }
    }

    private RecordQueryService records() {
        return new RecordQueryService(runs, members, crews, races, rewards, policy, clock);
    }

    private RunSummary ownersRun() {
        RunSummary summary = run(OTHER, NOW.minusHours(2), 5_000, 1_500);
        when(runs.summary(summary.recordId())).thenReturn(summary);
        when(runs.detail(eq(summary.recordId()), any()))
                .thenReturn(
                        new RunDetail(
                                summary,
                                List.of(),
                                null,
                                Map.of(HeartRateZone.Z3, Duration.ofMinutes(10)),
                                List.of(
                                        new UnstableSegment(
                                                summary.startedAt().plusMinutes(1),
                                                summary.startedAt().plusMinutes(2),
                                                Distance.ofMeters(200))),
                                new Weather(12, SkyCondition.CLEAR),
                                List.of(
                                        new NewRecordMark(
                                                NewRecordMark.Kind.LONGEST, null, "직전 4.20 km"),
                                        new NewRecordMark(
                                                NewRecordMark.Kind.BASE_DISTANCE,
                                                StandardDistance.KM5,
                                                "26:00")),
                                new HeartRateSummary(150, 172),
                                List.of(
                                        new PauseInterval(
                                                summary.startedAt().plusMinutes(10),
                                                summary.startedAt().plusMinutes(12)))));
        when(runs.routePoints(summary.recordId())).thenReturn(List.of(new GeoPoint(37.5, 127.0)));
        return summary;
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "기록 공개 판정 3.2 규칙 1~4: 본인 전부, 비공개 거부, 크루 공개·다른 크루 거부, 공개는 코스지도공유일 때만 지도")
    void detail_decisionTable() {
        RunSummary summary = ownersRun();
        RecordQueryService service = records();
        when(races.contributionOf(summary.recordId())).thenReturn(Distance.ofKm(5));
        when(rewards.accruedFor(AccrualReason.VALID_RUN, summary.recordId(), OTHER)).thenReturn(50);
        when(members.ageOf(eq(OTHER), anyInt())).thenReturn(OptionalInt.of(30));
        preference(OTHER, RecordVisibility.PRIVATE, false);

        RecordDetailView own = service.detail(OTHER, summary.recordId());
        assertThat(own.owner()).isTrue();
        assertThat(own.route()).hasSize(1);
        assertThat(own.crewContribution()).isEqualTo(Distance.ofKm(5));
        assertThat(own.rewardPoints()).isEqualTo(50);
        assertThat(own.calories()).isEqualTo(325);
        assertThat(own.heartRateZones()).containsKey("Z3");
        assertThat(own.newRecords()).containsExactly("최장 거리 신기록 · 직전 4.20 km", "5 km 신기록");
        assertThat(own.unstable().get(0).from()).isEqualTo(Duration.ofMinutes(1));
        assertThat(own.pauses())
                .singleElement()
                .satisfies(
                        p -> {
                            assertThat(p.from()).isEqualTo(summary.startedAt().plusMinutes(10));
                            assertThat(p.to()).isEqualTo(summary.startedAt().plusMinutes(12));
                        });
        assertThat(own.pausedSeconds()).isEqualTo(120);
        assertThat(own.recordVisibility()).isEqualTo(RecordVisibility.PRIVATE);
        verify(runs).detail(summary.recordId(), OptionalInt.of(190));

        assertThatThrownBy(() -> service.detail(ME, summary.recordId()))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.RECORD_PRIVATE);

        preference(OTHER, RecordVisibility.CREW, true);
        assertThatThrownBy(() -> service.detail(ME, summary.recordId()))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.RECORD_CREW_ONLY);
        when(crews.sameCrew(ME, OTHER)).thenReturn(true);
        assertThat(service.detail(ME, summary.recordId()).route()).hasSize(1);

        preference(OTHER, RecordVisibility.PUBLIC, false);
        RecordDetailView other = service.detail(ME, summary.recordId());
        assertThat(other.owner()).isFalse();
        assertThat(other.route()).isEmpty();
        assertThat(other.heartRateZones()).isEmpty();
        assertThat(other.crewContribution()).isNull();
        assertThat(other.rewardPoints()).isNull();
        assertThat(other.recordVisibility()).isNull();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("기록 목록: 공개 판정 뒤 최신순, 366일 넘는 기간은 400")
    void list() {
        RecordQueryService service = records();
        RunSummary a = run(OTHER, NOW.minusDays(1), 5_000, 1_500);
        when(runs.listRows(eq(OTHER), any()))
                .thenReturn(
                        List.of(
                                new com.a207.runkk.running.RunListRow(
                                        a,
                                        List.of(),
                                        com.a207.runkk.running.RouteShape.of(
                                                List.of(
                                                        new com.a207.runkk.common.GeoPoint(
                                                                37.5, 127.0),
                                                        new com.a207.runkk.common.GeoPoint(
                                                                37.6, 127.1))))));
        preference(OTHER, RecordVisibility.PUBLIC, false);
        DateRange month = new DateRange(TODAY.minusDays(30), TODAY);
        assertThat(service.list(ME, OTHER, month).runs().get(0).routeShape())
                .as("코스지도공유가 꺼진 다른 러너에게는 경로 모양을 감춘다(D-234)")
                .isNull();
        assertThat(service.list(OTHER, OTHER, month).runs().get(0).routeShape()).isNotNull();
        assertThat(service.list(ME, OTHER, month).runs())
                .extracting(RecordRow::recordId)
                .containsExactly(a.recordId());
        assertThatThrownBy(
                        () -> service.list(ME, OTHER, new DateRange(TODAY.minusDays(400), TODAY)))
                .isInstanceOf(IllegalArgumentException.class);
        preference(OTHER, RecordVisibility.PRIVATE, false);
        assertThatThrownBy(() -> service.list(ME, OTHER, month))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("카드: 본인 기록만(남의 카드는 403), 앞면 값 + 경로 + 코스지도공유 값")
    void cardData() {
        RunSummary summary = ownersRun();
        preference(OTHER, RecordVisibility.PUBLIC, true);
        when(members.summaries(List.of(OTHER)))
                .thenReturn(
                        Map.of(
                                OTHER,
                                new MemberSummary(
                                        OTHER,
                                        DisplayName.of("달리는곰"),
                                        Tier.BRONZE,
                                        ProfileVisibility.PUBLIC)));
        RunCardData card = records().cardData(OTHER, summary.recordId());
        assertThat(card.nickname()).isEqualTo("달리는곰");
        assertThat(card.cardKind()).isEqualTo("FREE_RUN");
        assertThat(card.averageHeartRate()).isEqualTo(150);
        assertThat(card.route()).hasSize(1);
        assertThat(card.courseMapShared()).isTrue();
        assertThatThrownBy(() -> records().cardData(ME, summary.recordId()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("성장: 주간 8주, 기준 거리 최고 기록, 최장 거리")
    void growth() {
        when(runs.periodTotals(ME, AggregationPeriod.WEEKLY, 8))
                .thenReturn(List.of(new PeriodStats(TODAY, TODAY, Distance.ofKm(5), 1, null)));
        when(runs.best(eq(ME), any())).thenReturn(Optional.empty());
        when(runs.best(ME, StandardDistance.KM5))
                .thenReturn(Optional.of(new BestEntry("r1", 1_500_000L, null, TODAY, true)));
        when(runs.longest(ME))
                .thenReturn(Optional.of(new BestEntry("r2", null, 12_000L, TODAY, false)));
        GrowthView view = new GrowthService(runs).growth(ME, AggregationPeriod.WEEKLY);
        assertThat(view.periods()).hasSize(1);
        assertThat(view.bests()).containsOnlyKeys(StandardDistance.KM5);
        assertThat(view.bests().get(StandardDistance.KM5).time()).isEqualTo(Duration.ofMinutes(25));
        assertThat(view.bests().get(StandardDistance.KM5).hasUnstable()).isTrue();
        assertThat(view.longest().distance()).isEqualTo(Distance.ofKm(12));
        verify(runs).best(ME, StandardDistance.FULL); // 풀 최고 기록도 성장 화면에 싣는다(v0.14 D-238)
    }

    @Test
    @org.junit.jupiter.api.DisplayName("코치: 목표·계획 저장, 지난 날짜 거부, 이행 현황·오늘 계획·보충 추천")
    void coach() {
        CoachService coach = new CoachService(goals, runs, notifications, policy, clock);
        assertThat(coach.setWeeklyGoal(ME, new WeeklyGoalCommand(3, Distance.ofKm(15))).count())
                .isEqualTo(3);
        assertThatThrownBy(
                        () -> coach.setWeeklyGoal(ME, new WeeklyGoalCommand(9, Distance.ofKm(15))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(
                        () ->
                                coach.putPlan(
                                        ME,
                                        new DailyPlan(TODAY.minusDays(1), Distance.ofKm(3), null)))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.PAST_DATE);
        coach.putPlan(ME, new DailyPlan(TODAY, Distance.ofKm(4), null));
        assertThat(coach.todayPlan(ME).distance()).isEqualTo(Distance.ofKm(4));
        assertThat(coach.todayPlan(ME).supplement()).isEqualTo(Distance.ofKm(4));
        when(runs.dailyStats(eq(ME), any()))
                .thenReturn(
                        Map.of(
                                TODAY,
                                new DayStats(TODAY, Distance.ofKm(1), Duration.ofMinutes(6), 1)));
        PlanProgressView progress = coach.progress(ME, new DateRange(TODAY.minusDays(1), TODAY));
        assertThat(progress.days()).hasSize(2);
        PlanProgressView.Day today = progress.days().get(1);
        assertThat(today.planned()).isEqualTo(Distance.ofKm(4));
        assertThat(today.shortfall()).isEqualTo(Distance.ofKm(3));
        assertThat(today.supplement()).isEqualTo(Distance.ofKm(4));
        assertThat(progress.days().get(0).planned()).isNull();
        assertThat(
                        new CoachService(goals, runs, notifications, policy, clock)
                                .todayPlan(OTHER)
                                .hasPlan())
                .isFalse();
        assertThatThrownBy(() -> coach.progress(ME, new DateRange(TODAY.minusDays(100), TODAY)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("코치: 오늘 계획 취소는 저장되고 다시 불러도 오류가 없다, 지난 날짜는 PAST_DATE")
    void coach_deletePlan() {
        CoachService coach = new CoachService(goals, runs, notifications, policy, clock);
        coach.putPlan(ME, new DailyPlan(TODAY, Distance.ofKm(4), null));

        coach.deletePlan(ME, TODAY);
        coach.deletePlan(ME, TODAY);

        assertThat(coach.todayPlan(ME).hasPlan()).isFalse();
        assertThat(goals.findOrCreate(ME).planOn(TODAY)).isEmpty();
        assertThatThrownBy(() -> coach.deletePlan(ME, TODAY.minusDays(1)))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.PAST_DATE);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("3시 정리: 1년이 지난 날짜별 계획·주간 목표만 지운다")
    void purgePastPlans() {
        RunningGoal goal = goals.findOrCreate(ME);
        goal.putPlan(
                new DailyPlan(TODAY.minusYears(2), Distance.ofKm(3), null), TODAY.minusYears(2));
        goal.putPlan(new DailyPlan(TODAY, Distance.ofKm(3), null), TODAY);
        goals.save(goal);
        CoachService coach = new CoachService(goals, runs, notifications, policy, clock);
        assertThat(coach.purgePastPlans(TODAY)).isEqualTo(1);
        assertThat(goals.findOrCreate(ME).planOn(TODAY)).isPresent();
        assertThat(goals.findOrCreate(ME).planOn(TODAY.minusYears(2))).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("랭킹 부분: 상위 N줄과 본인 줄, 바로 위 순위까지 거리(같은 거리는 같은 순위)")
    void rankingPage_andGaps() {
        RankingSnapshot snapshot =
                RankingSnapshot.build(
                        RankingKind.PERSONAL,
                        com.a207.runkk.common.AggregationPeriod.WEEKLY,
                        NOW,
                        List.of(score(1, 10), score(2, 8), score(3, 8), score(4, 5)));
        RankingPage page = snapshot.page(2, java.util.Optional.of(4L));
        assertThat(page.top()).extracting(RankEntry::subjectId).containsExactly(1L, 2L);
        assertThat(page.mine().rank()).isEqualTo(4);
        assertThat(page.gapToNext()).isEqualTo(Distance.ofKm(3));
        assertThat(snapshot.gapsToNext())
                .containsEntry(2L, Distance.ofKm(2))
                .containsEntry(3L, Distance.ofKm(2))
                .containsEntry(4L, Distance.ofKm(3))
                .doesNotContainKey(1L);
    }

    private static RankScore score(long id, long km) {
        return new RankScore(
                id, com.a207.runkk.common.DisplayName.of("러너" + id), null, Distance.ofKm(km));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("20시 미이행 알림: 오늘 계획이 있고 오늘 기록 0인 회원에게만 PLAN_MISSED(FR-075)")
    void remindMissedPlans() {
        CoachService coach = new CoachService(goals, runs, notifications, policy, clock);
        coach.putPlan(ME, new DailyPlan(TODAY, Distance.ofKm(4), null));
        coach.putPlan(OTHER, new DailyPlan(TODAY, Distance.ofKm(4), null));
        when(runs.distancesByMember(anyCollection(), any()))
                .thenReturn(Map.of(ME, Distance.ZERO, OTHER, Distance.ofKm(5)));
        assertThat(coach.remindMissedPlans(NOW.withHour(20))).isEqualTo(1);
        verify(notifications, times(1)).notify(any(NotificationRequest.class));
        verify(notifications)
                .notify(
                        org.mockito.ArgumentMatchers.argThat(
                                r ->
                                        r.recipientId() == ME
                                                && r.type() == NotificationType.PLAN_MISSED));
        assertThat(coach.remindMissedPlans(NOW.plusDays(5))).isZero();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "랭킹: 0시 개인·크루 × 주간·월간 4개 스냅숏, 거리 조회는 기간마다 한 번, 나만 보기·크루 없음 안내")
    void ranking() {
        RankingService service = new RankingService(snapshots, members, crews, runs, tx, clock);
        when(members.rankedMemberIds()).thenReturn(Set.of(ME, OTHER));
        when(crews.activeMembersByCrew()).thenReturn(Map.of(7L, List.of(ME, 3L)));
        when(members.summaries(any()))
                .thenReturn(
                        Map.of(
                                ME, summaryOf(ME, "나"),
                                OTHER, summaryOf(OTHER, "너")));
        when(crews.crewNames(any())).thenReturn(Map.of(7L, DisplayName.of("새벽크루")));
        when(runs.distancesByMember(anyCollection(), any()))
                .thenReturn(
                        Map.of(
                                ME,
                                Distance.ofKm(10),
                                OTHER,
                                Distance.ofKm(20),
                                3L,
                                Distance.ofKm(4)));

        service.rebuild(NOW.withHour(0));

        verify(runs, times(2)).distancesByMember(anyCollection(), any());
        assertThat(snapshots.rows).hasSize(4);
        RankingView personal = service.personalRanking(ME, AggregationPeriod.WEEKLY);
        assertThat(personal.entries()).extracting(RankEntry::subjectId).containsExactly(OTHER, ME);
        assertThat(personal.mine().rank()).isEqualTo(2);
        assertThat(personal.gapToNext()).isEqualTo(Distance.ofKm(10));
        assertThat(personal.notice()).isNull();
        assertThat(personal.shownGenders()).as("표시 성별은 조회 때 붙인다(스냅숏 밖, D-334)").isEmpty();
        when(members.shownGendersOf(anyCollection()))
                .thenReturn(Map.of(ME, Gender.FEMALE, OTHER, Gender.MALE));
        RankingView shown = service.personalRanking(ME, AggregationPeriod.WEEKLY);
        assertThat(shown.shownGenderOf(shown.entries().get(0))).isEqualTo("MALE");
        assertThat(shown.shownGenderOf(shown.mine())).isEqualTo("FEMALE");
        when(crews.activeCrewOf(ME)).thenReturn(Optional.of(new CrewSummary(7, "새벽크루", null)));
        RankingView crew = service.crewRanking(ME, AggregationPeriod.MONTHLY);
        assertThat(crew.mine().distance()).isEqualTo(Distance.ofKm(7));
        assertThat(crew.mine().displayName().value()).isEqualTo("새벽크루");
        assertThat(crew.shownGenders()).as("크루 줄에는 표시 성별이 없다").isEmpty();

        preference(ME, RecordVisibility.PRIVATE, false);
        assertThat(service.personalRanking(ME, AggregationPeriod.WEEKLY).notice())
                .isEqualTo(RankingService.PRIVATE_NOTICE);
        when(crews.activeCrewOf(ME)).thenReturn(Optional.empty());
        assertThat(service.crewRanking(ME, AggregationPeriod.MONTHLY).notice())
                .isEqualTo(RankingService.NO_CREW_NOTICE);
        snapshots.rows.clear();
        assertThat(service.crewRanking(ME, AggregationPeriod.MONTHLY).aggregatedAt()).isNull();
        service.rebuildIfMissing();
        assertThat(snapshots.rows).hasSize(4);
        snapshots.rows.values().forEach(s -> assertThat(s.getComputedAt()).isEqualTo(NOW));
        service.rebuildIfMissing();
        verify(runs, times(4)).distancesByMember(anyCollection(), any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5802 랭킹 공개 티어는 조회 때 붙인다: 스냅숏 tier(0시 값)는 그대로, publicTier는 지금 회원 티어, 탈퇴 회원·크루 줄은"
                    + " 없음(D-486)")
    void ranking_publicTierAttachedAtQueryTime() {
        RankingService service = new RankingService(snapshots, members, crews, runs, tx, clock);
        when(members.rankedMemberIds()).thenReturn(Set.of(ME, OTHER));
        when(crews.activeMembersByCrew()).thenReturn(Map.of(7L, List.of(ME)));
        when(members.summaries(any()))
                .thenReturn(Map.of(ME, summaryOf(ME, "나"), OTHER, summaryOf(OTHER, "너")));
        when(crews.crewNames(any())).thenReturn(Map.of(7L, DisplayName.of("새벽크루")));
        when(runs.distancesByMember(anyCollection(), any()))
                .thenReturn(Map.of(ME, Distance.ofKm(10), OTHER, Distance.ofKm(20)));
        service.rebuild(NOW.withHour(0));
        // 0시 뒤 OTHER가 배치고사를 마쳐 플래티넘, ME는 아직 배치 중(언랭)
        when(members.summaries(any()))
                .thenReturn(
                        Map.of(
                                ME,
                                new MemberSummary(
                                        ME,
                                        DisplayName.of("나"),
                                        Tier.UNRANKED,
                                        ProfileVisibility.PUBLIC),
                                OTHER,
                                new MemberSummary(
                                        OTHER,
                                        DisplayName.of("너"),
                                        Tier.PLATINUM,
                                        ProfileVisibility.PUBLIC)));

        RankingView personal = service.personalRanking(ME, AggregationPeriod.WEEKLY);

        RankEntry top = personal.entries().get(0);
        assertThat(top.tier()).as("스냅숏 값 그대로").isEqualTo(Tier.GOLD);
        assertThat(personal.publicTierOf(top)).isEqualTo(Tier.PLATINUM);
        assertThat(personal.publicTierOf(personal.mine())).isEqualTo(Tier.UNRANKED);

        when(members.summaries(any()))
                .thenReturn(
                        Map.of(
                                OTHER,
                                new MemberSummary(
                                        OTHER,
                                        DisplayName.WITHDRAWN,
                                        Tier.UNRANKED,
                                        ProfileVisibility.PRIVATE)));
        RankingView afterWithdrawal = service.personalRanking(ME, AggregationPeriod.WEEKLY);
        assertThat(afterWithdrawal.publicTierOf(afterWithdrawal.entries().get(0))).isNull();
        when(crews.activeCrewOf(ME)).thenReturn(Optional.of(new CrewSummary(7, "새벽크루", null)));
        RankingView crew = service.crewRanking(ME, AggregationPeriod.WEEKLY);
        assertThat(crew.publicTierOf(crew.mine())).isNull();
        assertThat(crew.publicTiers()).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5803 본인 줄도 공개 티어다: 배치 중이면 UNRANKED이고 임시 티어는 랭킹 어디에도 없다(D-485)")
    void ranking_ownRowHasNoProvisionalTier() {
        RankingService service = new RankingService(snapshots, members, crews, runs, tx, clock);
        when(members.rankedMemberIds()).thenReturn(Set.of(ME));
        when(crews.activeMembersByCrew()).thenReturn(Map.of());
        when(members.summaries(any()))
                .thenReturn(
                        Map.of(
                                ME,
                                new MemberSummary(
                                        ME,
                                        DisplayName.of("나"),
                                        Tier.UNRANKED,
                                        ProfileVisibility.PUBLIC)));
        when(runs.distancesByMember(anyCollection(), any()))
                .thenReturn(Map.of(ME, Distance.ofKm(10)));
        service.rebuild(NOW.withHour(0));

        RankingView personal = service.personalRanking(ME, AggregationPeriod.WEEKLY);

        assertThat(personal.publicTierOf(personal.mine())).isEqualTo(Tier.UNRANKED);
        assertThat(personal.publicTiers().values()).containsOnly(Tier.UNRANKED);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5804 대시보드 latestRun = 기록 목록 맨 위 1건(유효·무효 모두, 최근 30일에 없으면 전체 기간), 기록이 없으면"
                    + " null(D-491)")
    void dashboard_latestRunIsTopOfRecordList() {
        DashboardService service =
                new DashboardService(runs, goals, crews, members, policy, rewards, clock);
        when(runs.summariesBetween(eq(ME), any())).thenReturn(List.of());
        when(runs.dailyStats(eq(ME), any())).thenReturn(Map.of());
        when(runs.totals(ME)).thenReturn(new TotalStats(Distance.ZERO, Duration.ZERO, 0));
        when(crews.activeCrewOf(ME)).thenReturn(Optional.empty());

        assertThat(service.dashboard(ME, null).latestRun()).isNull();

        RunSummary old = run(ME, NOW.minusDays(200), 1_000, 400);
        when(runs.latestListRow(ME))
                .thenReturn(
                        Optional.of(new com.a207.runkk.running.RunListRow(old, List.of(), null)));

        RecordRow latest = service.dashboard(ME, TODAY.minusMonths(3)).latestRun();
        // PL1-B5: 전 기간 목록을 읽지 않고 최신 1건만 읽는다
        verify(runs, never()).listRows(eq(ME), any());

        assertThat(latest.recordId()).isEqualTo(old.recordId());
        assertThat(latest.distance()).isEqualTo(Distance.ofMeters(1_000));
        assertThat(latest.battleId()).isNull();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("운영자 재계산: 지금 시각으로 4개 스냅숏을 다시 만들고 개인·크루 건수를 돌려준다")
    void recompute_rebuildsNowAndReportsCounts() {
        RankingService service = new RankingService(snapshots, members, crews, runs, tx, clock);
        when(members.rankedMemberIds()).thenReturn(Set.of(ME, OTHER));
        when(crews.activeMembersByCrew()).thenReturn(Map.of(7L, List.of(ME, 3L)));
        when(members.summaries(any()))
                .thenReturn(Map.of(ME, summaryOf(ME, "나"), OTHER, summaryOf(OTHER, "너")));
        when(crews.crewNames(any())).thenReturn(Map.of(7L, DisplayName.of("새벽크루")));
        when(runs.distancesByMember(anyCollection(), any()))
                .thenReturn(Map.of(ME, Distance.ofKm(10), OTHER, Distance.ofKm(20)));

        RankingRecomputeResult result = service.recompute();

        assertThat(result).isEqualTo(new RankingRecomputeResult(NOW, 2, 1, 0));
        assertThat(snapshots.rows).hasSize(4);
        snapshots.rows.values().forEach(s -> assertThat(s.getComputedAt()).isEqualTo(NOW));
        assertThat(service.personalRanking(ME, AggregationPeriod.WEEKLY).mine().rank())
                .isEqualTo(2);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("재계산 저장 실패는 삼키지 않고 실패한 스냅숏 수로 알린다")
    void recompute_reportsFailedSnapshots() {
        RankingSnapshotRepository failing = mock(RankingSnapshotRepository.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("db"))
                .when(failing)
                .save(org.mockito.ArgumentMatchers.argThat(s -> s.getKind() == RankingKind.CREW));
        RankingService service = new RankingService(failing, members, crews, runs, tx, clock);

        assertThat(service.recompute().failedSnapshots()).isEqualTo(2);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "재계산 중 또 부르면 IllegalStateException(409), 0시 작업은 끝날 때까지 기다렸다 이어서 집계한다")
    void recompute_whileRunning_isRejected_andMidnightRebuildWaits() throws Exception {
        RankingService service = new RankingService(snapshots, members, crews, runs, tx, clock);
        java.util.concurrent.CountDownLatch inside = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger calls =
                new java.util.concurrent.atomic.AtomicInteger();
        when(members.rankedMemberIds())
                .thenAnswer(
                        inv -> {
                            if (calls.incrementAndGet() == 1) {
                                inside.countDown();
                                release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                            }
                            return Set.of(ME);
                        });
        when(members.summaries(any())).thenReturn(Map.of(ME, summaryOf(ME, "나")));
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<RankingRecomputeResult> first =
                    pool.submit(service::recompute);
            assertThat(inside.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(service::recompute).isInstanceOf(IllegalStateException.class);
            java.util.concurrent.Future<?> midnight =
                    pool.submit(() -> service.rebuild(NOW.withHour(0)));
            assertThatThrownBy(() -> midnight.get(300, java.util.concurrent.TimeUnit.MILLISECONDS))
                    .as("0시 작업은 재계산이 끝날 때까지 들어오지 못한다")
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            assertThat(calls).hasValue(1);

            release.countDown();
            assertThat(first.get(5, java.util.concurrent.TimeUnit.SECONDS).personalCount())
                    .isEqualTo(1);
            midnight.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(calls).hasValue(2);
        assertThat(snapshots.rows).hasSize(4);
        snapshots
                .rows
                .values()
                .forEach(s -> assertThat(s.getComputedAt()).isEqualTo(TODAY.atStartOfDay()));
        assertThat(service.recompute().computedAt()).as("끝난 뒤에는 다시 부를 수 있다").isEqualTo(NOW);
    }

    private static MemberSummary summaryOf(long id, String name) {
        return new MemberSummary(id, DisplayName.of(name), Tier.GOLD, ProfileVisibility.PUBLIC);
    }

    private RunRecordSaved saved(int ordinal, boolean valid) {
        return new RunRecordSaved(
                "r" + ordinal,
                ME,
                NOW,
                Distance.ofKm(5),
                Duration.ofMinutes(25),
                valid,
                40.0,
                RunMode.FREE,
                null,
                null,
                true,
                valid ? ordinal : 0,
                NOW);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "리포트: 유효 러닝만, 같은 사건 재수신은 코멘트를 다시 만들지 않는다(T-16), 없으면 '유효 러닝 기록 없음'")
    void report() {
        ReportCommentService comments = mock(ReportCommentService.class);
        ReportService service = new ReportService(reports, runs, comments, tx, clock);
        assertThat(service.report(ME).status()).isEqualTo(ReportView.Status.NO_VALID_RUN);
        List<RunSummary> recent = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            recent.add(run(ME, NOW.minusDays(i), 5_000, 1_500));
        }
        when(runs.recentSummaries(ME, ReportService.SUMMARY_RUNS)).thenReturn(recent);

        service.onRunSaved(saved(12, false));
        verify(comments, never()).generateComment(anyLong());
        service.onRunSaved(saved(12, true));
        ReportView view = service.report(ME);
        assertThat(view.fromRun()).isEqualTo(3);
        assertThat(view.toRun()).isEqualTo(12);
        assertThat(view.message()).isEqualTo("코멘트 준비 중");
        // 앞선 코멘트 생성이 끊겨 코멘트가 없으면 같은 사건 재수신에 다시 만든다
        service.onRunSaved(saved(12, true));
        verify(comments, times(2)).generateComment(ME);
        // 코멘트가 붙은 뒤의 같은 사건 재수신은 다시 만들지 않는다(T-16)
        reports.findByMember(ME)
                .orElseThrow()
                .attachComment(new Comment("좋아요", CommentSource.RULE, 12));
        service.onRunSaved(saved(12, true));
        verify(comments, times(2)).generateComment(ME);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("코멘트 서비스: 동의·스위치로 문맥을 만들고 같은 요약 순번이면 붙인다")
    void reportComment() {
        reports.save(Report.create(ME, AnalysisFixtures.summary(12, 0, 0), NOW));
        CommentGenerator generator = mock(CommentGenerator.class);
        when(generator.generate(any(), any()))
                .thenReturn(Optional.of(new Comment("좋아요", CommentSource.AI, 12)));
        new ReportCommentService(reports, generator, members, policy, tx, clock)
                .generateComment(ME);
        verify(generator).generate(any(), eq(new CommentContext(true, true, ME, TODAY)));
        assertThat(reports.findByMember(ME).orElseThrow().view().comment()).isEqualTo("좋아요");
        new ReportCommentService(reports, generator, members, policy, tx, clock)
                .generateComment(OTHER);
        verify(generator, times(1)).generate(any(), any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("탈퇴: 목표·계획과 리포트를 지운다(D3, D4)")
    void withdrawal() {
        goals.save(RunningGoal.create(ME));
        reports.save(Report.create(ME, AnalysisFixtures.summary(3, 0, 0), NOW));
        new AnalysisWithdrawalListener(goals, reports)
                .onMemberWithdrawn(new MemberWithdrawn(ME, NOW));
        assertThat(goals.rows).isEmpty();
        assertThat(reports.rows).isEmpty();
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
