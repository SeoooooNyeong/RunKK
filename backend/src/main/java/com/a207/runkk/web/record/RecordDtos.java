package com.a207.runkk.web.record;

import com.a207.runkk.analysis.DashboardView;
import com.a207.runkk.analysis.Grass;
import com.a207.runkk.analysis.GrowthView;
import com.a207.runkk.analysis.PlanProgressView;
import com.a207.runkk.analysis.RankEntry;
import com.a207.runkk.analysis.RankingView;
import com.a207.runkk.analysis.RecordDetailView;
import com.a207.runkk.analysis.RecordRow;
import com.a207.runkk.analysis.ReportView;
import com.a207.runkk.analysis.RunCardData;
import com.a207.runkk.analysis.TodayPlanView;
import com.a207.runkk.analysis.WeeklyAchievement;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.common.Pace;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 기록 분석 REST 전송 형식(5.7.1 web.record). 항목 이름은 앱 api.record DTO에 맞추고(거리 미터, 시간 초, 페이스 km당 초, 날짜
 * yyyy-MM-dd, 시각 +09:00 오프셋), 설계가 더 요구하는 항목은 덧붙인다. 앱과 다른 점은 B5 보고서의 "앱 DTO와 다른 항목"에 적었다.
 */
final class RecordDtos {

    private RecordDtos() {}

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int HOURS_PER_BAND = 3;
    private static final double PERCENT = 100.0;

    /** 서버 시각(Asia/Seoul LocalDateTime) → +09:00 오프셋 시각. */
    static OffsetDateTime seoul(LocalDateTime time) {
        return time == null ? null : time.atZone(SEOUL).toOffsetDateTime();
    }

    static Long meters(Distance d) {
        return d == null ? null : d.meters();
    }

    static Integer seconds(Pace p) {
        return p == null ? null : p.secondsPerKm();
    }

    static Map<String, Long> grassOf(Grass grass) {
        Map<String, Long> cells = new LinkedHashMap<>();
        grass.cells().forEach((day, d) -> cells.put(day.toString(), d.meters()));
        return cells;
    }

    static Map<String, Integer> levelsOf(Grass grass) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        grass.cells().keySet().forEach(day -> levels.put(day.toString(), grass.intensityOn(day)));
        return levels;
    }

    /** 기록 한 줄(앱 RunSummaryDto). */
    record RunSummaryDto(
            String recordId,
            OffsetDateTime startedAt,
            long distanceMeters,
            long elapsedSeconds,
            Integer averagePaceSecondsPerKm,
            boolean valid,
            String mode,
            String cardKind) {

        static RunSummaryDto of(RecordRow row) {
            return new RunSummaryDto(
                    row.recordId(),
                    seoul(row.startedAt()),
                    row.distance().meters(),
                    row.elapsed().toSeconds(),
                    seconds(row.averagePace()),
                    row.valid(),
                    row.mode(),
                    row.cardKind());
        }
    }

    /** 좌표 하나(앱 GeoPointDto). */
    record GeoPointDto(double latitude, double longitude) {

        static List<GeoPointDto> of(List<GeoPoint> points) {
            return points.stream().map(p -> new GeoPointDto(p.latitude(), p.longitude())).toList();
        }
    }

    /** 대시보드(앱 DashboardResponse + 시간·횟수·주간 목표·달력·잔디 단계). */
    record DashboardResponse(
            LocalDate baseDate,
            String unit,
            long todayMeters,
            long todaySeconds,
            int todayRuns,
            long totalMeters,
            long totalSeconds,
            int totalRuns,
            String landmark,
            int streakDays,
            Map<String, Long> grass,
            Map<String, Integer> grassLevels,
            Double weeklyAchievement,
            WeeklyDto weekly,
            CrewArea crew,
            Map<String, List<CalendarRun>> calendar) {

        static DashboardResponse of(DashboardView v) {
            Map<String, List<CalendarRun>> calendar = new LinkedHashMap<>();
            v.calendar()
                    .forEach(
                            (day, runs) ->
                                    calendar.put(
                                            day.toString(),
                                            runs.stream()
                                                    .map(
                                                            r ->
                                                                    new CalendarRun(
                                                                            r.recordId(),
                                                                            r.distance().meters(),
                                                                            r.valid()))
                                                    .toList()));
            return new DashboardResponse(
                    v.baseDate(),
                    v.unit().name(),
                    v.today().distance().meters(),
                    v.today().elapsed().toSeconds(),
                    v.today().count(),
                    v.total().distance().meters(),
                    v.total().elapsed().toSeconds(),
                    v.total().count(),
                    v.landmark(),
                    v.streakDays(),
                    grassOf(v.grass()),
                    levelsOf(v.grass()),
                    v.weekly() == null ? null : v.weekly().distancePercent() / PERCENT,
                    v.weekly() == null ? null : WeeklyDto.of(v.weekly()),
                    v.crew() == null ? null : CrewArea.of(v.crew()),
                    calendar);
        }
    }

    /** 이번 주 목표 달성률(FR-056). */
    record WeeklyDto(
            LocalDate weekStart,
            int goalRuns,
            long goalMeters,
            int actualRuns,
            long actualMeters,
            int runsPercent,
            int distancePercent) {

        static WeeklyDto of(WeeklyAchievement a) {
            return new WeeklyDto(
                    a.goal().week().monday(),
                    a.goal().count(),
                    a.goal().distance().meters(),
                    a.actualCount(),
                    a.actualDistance().meters(),
                    a.countPercent(),
                    a.distancePercent());
        }
    }

    /** 크루 영역(앱 DashboardResponse.CrewArea). */
    record CrewArea(
            long crewId,
            String crewName,
            int streakDays,
            Map<String, Long> grass,
            Map<String, Integer> grassLevels,
            String landmark) {

        static CrewArea of(DashboardView.CrewArea c) {
            return new CrewArea(
                    c.crewId(),
                    c.crewName(),
                    c.streakDays(),
                    grassOf(c.grass()),
                    levelsOf(c.grass()),
                    c.landmark());
        }
    }

    /** 달력 칸의 기록. */
    record CalendarRun(String recordId, long distanceMeters, boolean valid) {}

    /** 기록 목록(앱 RecordListResponse). */
    record RecordListResponse(long ownerId, List<RunSummaryDto> runs) {}

    /** 불안정 구간(시작 기준 초). */
    record UnstableDto(long fromSeconds, long toSeconds, long distanceMeters) {}

    /** 불안정 구간의 경로 포인트 번호 닫힌 구간(앱 RangeDto). 위치 표본이 1초 간격이라(D-18) 시작 기준 초를 번호로 쓴다. */
    record RangeDto(int start, int end) {}

    /** 기록 상세(앱 RecordDetailResponse + 구간 페이스·심박 요약·날씨 항목). */
    record RecordDetailResponse(
            RunSummaryDto summary,
            boolean owner,
            List<GeoPointDto> route,
            List<RangeDto> unstableRanges,
            List<UnstableDto> unstableSegments,
            int calories,
            List<Integer> splitPaces,
            Integer fastestKmSecondsPerKm,
            Integer averageHeartRate,
            Integer maxHeartRate,
            Map<String, Long> heartRateZones,
            List<String> newRecords,
            Long crewContributionMeters,
            Integer rewardPoints,
            String weather,
            Integer temperatureC,
            String sky) {

        static RecordDetailResponse of(RecordDetailView v) {
            Map<String, Long> zones = new LinkedHashMap<>();
            v.heartRateZones().forEach((zone, time) -> zones.put(zone, time.toSeconds()));
            RecordDetailView.WeatherInfo w = v.weather();
            return new RecordDetailResponse(
                    RunSummaryDto.of(v.summary()),
                    v.owner(),
                    GeoPointDto.of(v.route()),
                    v.unstable().stream()
                            .map(
                                    u ->
                                            new RangeDto(
                                                    (int) u.from().toSeconds(),
                                                    (int) u.to().toSeconds()))
                            .toList(),
                    v.unstable().stream()
                            .map(
                                    u ->
                                            new UnstableDto(
                                                    u.from().toSeconds(),
                                                    u.to().toSeconds(),
                                                    u.distance().meters()))
                            .toList(),
                    v.calories(),
                    v.splitPaces().stream().map(Pace::secondsPerKm).toList(),
                    seconds(v.fastestKm()),
                    v.heartRate() == null ? null : v.heartRate().average(),
                    v.heartRate() == null ? null : v.heartRate().max(),
                    zones,
                    v.newRecords(),
                    meters(v.crewContribution()),
                    v.rewardPoints(),
                    w == null ? null : w.sky() + " " + w.temperatureC() + "°C",
                    w == null ? null : w.temperatureC(),
                    w == null ? null : w.sky());
        }
    }

    /** 러닝 카드 값(앱 RunCardResponse + 평균 심박·신기록). */
    record RunCardResponse(
            String recordId,
            String cardKind,
            OffsetDateTime startedAt,
            long distanceMeters,
            long elapsedSeconds,
            Integer averagePaceSecondsPerKm,
            Integer averageHeartRate,
            List<String> newRecords,
            List<GeoPointDto> route,
            boolean courseMapShared) {

        static RunCardResponse of(RunCardData c) {
            return new RunCardResponse(
                    c.recordId(),
                    c.cardKind(),
                    seoul(c.startedAt()),
                    c.distance().meters(),
                    c.elapsed().toSeconds(),
                    seconds(c.averagePace()),
                    c.averageHeartRate(),
                    c.newRecords(),
                    GeoPointDto.of(c.route()),
                    c.courseMapShared());
        }
    }

    /** 성장 분석(앱 GrowthResponse + 기간 끝·최고 기록 상세). */
    record GrowthResponse(
            String period,
            List<PeriodTotal> periods,
            Map<String, Long> bests,
            Map<String, BestDto> bestRecords,
            Long longestMeters,
            BestDto longest) {

        static GrowthResponse of(GrowthView v) {
            Map<String, Long> bests = new LinkedHashMap<>();
            Map<String, BestDto> records = new LinkedHashMap<>();
            v.bests()
                    .forEach(
                            (distance, best) -> {
                                bests.put(distance.name(), best.time().toSeconds());
                                records.put(distance.name(), BestDto.of(best));
                            });
            GrowthView.Best longest = v.longest();
            return new GrowthResponse(
                    v.period().name(),
                    v.periods().stream().map(PeriodTotal::of).toList(),
                    bests,
                    records,
                    longest == null ? null : meters(longest.distance()),
                    longest == null ? null : BestDto.of(longest));
        }
    }

    /** 한 기간(앱 GrowthResponse.PeriodTotal + end). */
    record PeriodTotal(
            LocalDate start,
            LocalDate end,
            long distanceMeters,
            int runs,
            Integer averagePaceSecondsPerKm) {

        static PeriodTotal of(GrowthView.PeriodRow p) {
            return new PeriodTotal(
                    p.from(), p.to(), p.distance().meters(), p.count(), seconds(p.averagePace()));
        }
    }

    /** 최고 기록 상세(FR-063 불안정 구간 표시). */
    record BestDto(
            String recordId,
            Long seconds,
            Long distanceMeters,
            LocalDate achievedOn,
            boolean hasUnstable) {

        static BestDto of(GrowthView.Best b) {
            Duration time = b.time();
            return new BestDto(
                    b.recordId(),
                    time == null ? null : time.toSeconds(),
                    meters(b.distance()),
                    b.achievedOn(),
                    b.hasUnstable());
        }
    }

    /** 리포트(앱 ReportResponse의 comment·commentSource + 요약 항목). */
    record ReportResponse(
            String status,
            String message,
            String summary,
            String comment,
            String commentSource,
            Integer fromRun,
            Integer toRun,
            Double averageScore,
            Long distanceChangeMeters,
            Integer paceChangeSeconds,
            String frequentDay,
            Integer frequentHourBand,
            OffsetDateTime createdAt) {

        static ReportResponse of(ReportView v) {
            return new ReportResponse(
                    v.status().name(),
                    v.message(),
                    summaryText(v),
                    v.comment(),
                    v.commentSource() == null ? null : v.commentSource().name(),
                    v.fromRun(),
                    v.toRun(),
                    v.averageScore(),
                    v.distanceChangeMeters(),
                    v.paceChangeSeconds(),
                    v.frequentDay() == null ? null : v.frequentDay().name(),
                    v.frequentHourBand(),
                    seoul(v.createdAt()));
        }

        /** 앱 ReportResponse.summary 한 줄(요약이 없으면 null). */
        private static String summaryText(ReportView v) {
            if (v.toRun() == null) {
                return null;
            }
            int from = v.frequentHourBand() * HOURS_PER_BAND;
            return String.format(
                    Locale.KOREAN,
                    "%d~%d회 평균 점수 %.1f · 1회 거리 %+d m · 페이스 %+d초/km · 자주 달린 때 %s %02d~%02d시",
                    v.fromRun(),
                    v.toRun(),
                    v.averageScore(),
                    v.distanceChangeMeters(),
                    v.paceChangeSeconds(),
                    v.frequentDay().getDisplayName(TextStyle.FULL, Locale.KOREAN),
                    from,
                    from + HOURS_PER_BAND);
        }
    }

    /** 랭킹(앱 RankingResponse + 종류·기간). aggregatedAt은 스냅숏이 없으면 null이다. */
    record RankingResponse(
            String kind,
            String period,
            List<Entry> entries,
            Entry mine,
            Long gapToNextMeters,
            OffsetDateTime aggregatedAt,
            String notice) {

        static RankingResponse of(RankingView v) {
            return new RankingResponse(
                    v.kind().name(),
                    v.period().name(),
                    v.entries().stream().map(Entry::of).toList(),
                    v.mine() == null ? null : Entry.of(v.mine()),
                    meters(v.gapToNext()),
                    seoul(v.aggregatedAt()),
                    v.notice());
        }
    }

    /** 랭킹 한 줄(앱 RankingResponse.Entry + subjectId·tier). */
    record Entry(int rank, long subjectId, String name, String tier, long distanceMeters) {

        static Entry of(RankEntry e) {
            return new Entry(
                    e.rank(),
                    e.subjectId(),
                    e.displayName().value(),
                    e.tier() == null ? null : e.tier().name(),
                    e.distance().meters());
        }
    }

    /** 이행 현황(앱 PlanProgressResponse). */
    record PlanProgressResponse(List<Day> days) {

        static PlanProgressResponse of(PlanProgressView v) {
            return new PlanProgressResponse(v.days().stream().map(Day::of).toList());
        }
    }

    /** 하루(앱 PlanProgressResponse.Day + 페이스·부족 거리). */
    record Day(
            LocalDate date,
            Long plannedMeters,
            Integer targetPaceSecondsPerKm,
            long doneMeters,
            Integer actualPaceSecondsPerKm,
            Long shortfallMeters,
            Long supplementMeters) {

        static Day of(PlanProgressView.Day d) {
            return new Day(
                    d.date(),
                    meters(d.planned()),
                    seconds(d.targetPace()),
                    d.done().meters(),
                    seconds(d.actualPace()),
                    meters(d.shortfall()),
                    meters(d.supplement()));
        }
    }

    /** 오늘 계획(앱 TodayPlanResponse + 날짜·보충 거리). */
    record TodayPlanResponse(
            LocalDate date, Long distanceMeters, Integer paceSecondsPerKm, long supplementMeters) {

        static TodayPlanResponse of(TodayPlanView v) {
            return new TodayPlanResponse(
                    v.date(),
                    meters(v.distance()),
                    seconds(v.targetPace()),
                    v.supplement().meters());
        }
    }
}
