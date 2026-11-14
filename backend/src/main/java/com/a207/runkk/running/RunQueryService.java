package com.a207.runkk.running;

import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.AvatarLook;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 패키지가 읽을 러닝 기록 투영·통계를 내준다(설계 6.1.3, D-32, D-41). 돌려주는 값에는 RunRecord 엔티티가 없다. 다른 패키지(analysis,
 * battle, crew, runimal, avatar, profile)는 RunRecordRepository·PersonalRecordBookRepository를 쓰지 않고
 * 이 서비스만 부른다.
 */
@Service
@Transactional(readOnly = true)
public class RunQueryService {

    private static final Duration CURVE_STEP = Duration.ofSeconds(1);

    private final RunRecordRepository runRecordRepository;
    private final PersonalRecordBookRepository personalRecordBookRepository;
    private final Clock clock;

    public RunQueryService(
            RunRecordRepository runRecordRepository,
            PersonalRecordBookRepository personalRecordBookRepository,
            Clock clock) {
        this.runRecordRepository = runRecordRepository;
        this.personalRecordBookRepository = personalRecordBookRepository;
        this.clock = clock;
    }

    /** 회원이 지운 기록인가(묘비가 있다). 삭제 사건 수신자가 실패해 남은 기록 ID를 읽는 쪽이 거를 때 쓴다(결정 D67, U8-6). */
    public boolean isDeleted(String recordId) {
        return runRecordRepository.isBuried(recordId);
    }

    /**
     * 기록을 저장한 시점의 아바타 모습(설계 5.31 [임시:D-성-251]). 기록이 없거나 모습 없이 저장한 기록(옛 기록)이면 비어 있다. battle이 나와의 싸움
     * 상대("그날의 나")를 그릴 때 읽는다. 부르는 쪽 트랜잭션과 떨어진 새 읽기 트랜잭션에서 읽는다: 여기서 예외가 나도 부르는 쪽(기록 대결 시작 등) 트랜잭션이
     * rollback-only가 되지 않는다(S15P31A207-306). 부르는 쪽이 트랜잭션 안이면 읽는 동안 DB 연결을 하나 더 쓴다.
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<AvatarLook> lookOf(String recordId) {
        return runRecordRepository.findLook(recordId);
    }

    /** 기록 요약. 없으면 BusinessRuleException(RECORD_NOT_FOUND). */
    public RunSummary summary(String recordId) {
        return findSummary(recordId)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.RECORD_NOT_FOUND));
    }

    /**
     * summary와 같은 요약을 예외 없이 준다. 없으면 비어 있다. 읽기 트랜잭션에 참여한 호출에서 업무 예외를 잡으면 트랜잭션이 rollback-only가 되므로,
     * 요약이 없을 수 있는 기록은 이것으로 읽는다(배치고사 판 상태·셈, 코드 리뷰 PL1-B1).
     */
    public Optional<RunSummary> findSummary(String recordId) {
        return runRecordRepository.findSummary(recordId);
    }

    /**
     * 여러 기록의 요약을 한 번에(기록 ID → 요약). 없는 ID는 빠진다. 글·참가자마다 findSummary를 부르는 N+1을 피한다(crew 최근 크루런, 코드 리뷰
     * M3).
     */
    public Map<String, RunSummary> summariesOf(Collection<String> recordIds) {
        if (recordIds.isEmpty()) {
            return Map.of();
        }
        Map<String, RunSummary> byId = new HashMap<>();
        runRecordRepository
                .findSummaries(new LinkedHashSet<>(recordIds))
                .forEach(s -> byId.put(s.recordId(), s));
        return byId;
    }

    /** 최근 유효 기록 limit개, 최신순(analysis.ReportService). */
    public List<RunSummary> recentSummaries(long memberId, int limit) {
        return runRecordRepository.recentValidSummaries(memberId, limit);
    }

    /** 최근 유효 기록 limit개의 표본, 최신순(runimal). */
    public List<RunSampleRow> runSamples(long memberId, int limit) {
        return runRecordRepository.recentValidSamples(memberId, limit);
    }

    /** 기간 안 유효 기록의 표본, 최신순(runimal). */
    public List<RunSampleRow> runSamples(long memberId, DateRange r) {
        return runRecordRepository.validSamplesBetween(memberId, r);
    }

    /** 최근 유효 기록 limit개의 시작 일시, 최신순(avatar). */
    public List<LocalDateTime> recentStartTimes(long memberId, int limit) {
        return runRecordRepository.recentValidSamples(memberId, limit).stream()
                .map(RunSampleRow::startedAt)
                .toList();
    }

    /** 기간 안 기록 전부(무효 포함), 최신순. */
    public List<RunSummary> summariesBetween(long memberId, DateRange r) {
        return runRecordRepository.summariesBetween(memberId, r);
    }

    /**
     * 회원마다 시작 시각이 [from, to]이고 거리 minMeters 이상인 유효 기록 가운데 가장 이른 하나(회원 ID 순, event 이벤트 응모자 명단,
     * S15P31A207-275). 같은 시작 시각이면 기록 ID가 앞선 것이다.
     */
    public Map<Long, RunSummary> earliestValidRunsAtLeast(
            long minMeters, LocalDateTime from, LocalDateTime to) {
        Map<Long, RunSummary> first = new TreeMap<>();
        for (RunSummary run : runRecordRepository.validSummariesAtLeast(minMeters, from, to)) {
            first.putIfAbsent(run.memberId(), run);
        }
        return first;
    }

    /** 1km 구간 페이스, 가장 빠른 1km, 심박 구간(최대 심박이 있을 때), 불안정 구간, 날씨, 신기록, 정지 구간(FR-036). */
    public RunDetail detail(String recordId, OptionalInt maxHeartRate) {
        RunRecord record = load(recordId);
        return new RunDetail(
                record.summary(),
                record.splitPaces(),
                record.fastestKm().orElse(null),
                maxHeartRate.isPresent()
                        ? record.heartRateZones(maxHeartRate.getAsInt())
                        : Map.of(),
                record.unstableSegments(),
                record.getWeather(),
                record.getNewRecords(),
                record.heartRateSummary(),
                record.pauses(),
                record.isDeletable());
    }

    /**
     * 기간 안 기록 목록 줄(무효 포함), 최신순(v0.14 D-233·D-234·D-292). 신기록 종류와 경로 모양을 함께 읽고 위치·심박 열은 읽지 않는다(한 번의
     * 조회, N+1 없음).
     */
    public List<RunListRow> listRows(long memberId, DateRange r) {
        return runRecordRepository.listRows(memberId, r);
    }

    /** 기록 목록 맨 위 행 1건(무효 포함, 시작 시각 최신). 홈 최근 러닝이 전 기간 목록을 읽지 않게 한다(D-491, 코드 리뷰 PL1-B5). */
    public Optional<RunListRow> latestListRow(long memberId) {
        return runRecordRepository.latestListRow(memberId);
    }

    /** 그 기록이 일으킨 티어 평가(없으면 비어 있다, v0.14 D-291). 기록 상세와 대결 결과가 붙인다. */
    public Optional<EvaluationView> evaluationOf(String recordId) {
        return runRecordRepository.evaluationOf(recordId);
    }

    /** 대결 결과 비교표용(R5). 없으면 RECORD_NOT_FOUND. */
    public RunComparison comparisonOf(String recordId) {
        return comparisonOf(load(recordId));
    }

    /**
     * comparisonOf와 같은 값을 예외 없이 준다. 기록이 없으면(지워졌는데 기록 ID 정리가 아직, 기록 ID만 붙은 배치고사 판) 비어 있다. 대결 결과 조회가
     * 읽기 트랜잭션을 rollback-only로 만들지 않게 한다(코드 리뷰 PL1-B1, D-232).
     */
    public Optional<RunComparison> findComparison(String recordId) {
        return runRecordRepository.findById(recordId).map(RunQueryService::comparisonOf);
    }

    private static RunComparison comparisonOf(RunRecord record) {
        return new RunComparison(
                record.getElapsed(), record.getAveragePace(), record.fastestKm().orElse(null));
    }

    /** 날짜별 유효 거리·시간·횟수. */
    public Map<LocalDate, DayStats> dailyStats(long memberId, DateRange r) {
        return runRecordRepository.dailyStats(memberId, r);
    }

    /** 여러 회원의 날짜별 유효 거리 합(크루 스트릭·잔디). */
    public Map<LocalDate, Distance> dailyDistances(Collection<Long> memberIds, DateRange r) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        return runRecordRepository.dailyDistances(memberIds, r);
    }

    /** 누적 거리·시간·횟수·유효 거리. */
    public TotalStats totals(long memberId) {
        return runRecordRepository.totals(memberId);
    }

    /** 오늘이 속한 기간을 포함한 최근 count개 기간의 거리·횟수·평균 페이스(오래된 것부터). */
    public List<PeriodStats> periodTotals(long memberId, AggregationPeriod p, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("기간 수는 1 이상: " + count);
        }
        List<DateRange> ranges = new ArrayList<>();
        DateRange range = p.rangeOf(LocalDate.now(clock));
        for (int i = 0; i < count; i++) {
            ranges.add(range);
            range = p.rangeOf(range.start().minusDays(1));
        }
        Collections.reverse(ranges);
        DateRange whole = new DateRange(ranges.get(0).start(), ranges.get(count - 1).end());
        List<RunSummary> runs =
                runRecordRepository.summariesBetween(memberId, whole).stream()
                        .filter(RunSummary::valid)
                        .toList();
        List<PeriodStats> result = new ArrayList<>();
        for (DateRange r : ranges) {
            long meters = 0;
            long millis = 0;
            int n = 0;
            for (RunSummary run : runs) {
                if (r.contains(run.startedAt().toLocalDate())) {
                    meters += run.distance().meters();
                    millis += run.elapsed().toMillis();
                    n++;
                }
            }
            Pace pace =
                    meters == 0
                            ? null
                            : Pace.of(Duration.ofMillis(millis), Distance.ofMeters(meters));
            result.add(new PeriodStats(r.start(), r.end(), Distance.ofMeters(meters), n, pace));
        }
        return result;
    }

    /** 회원별 유효 거리 합(기록이 없으면 0). 랭킹, 크루 랜드마크, 주간 거리 재합산, 계획 미이행 확인. */
    public Map<Long, Distance> distancesByMember(Collection<Long> memberIds, DateRange r) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Distance> found = runRecordRepository.distancesByMember(memberIds, r);
        Map<Long, Distance> result = new HashMap<>();
        for (Long id : memberIds) {
            result.put(id, found.getOrDefault(id, Distance.ZERO));
        }
        return result;
    }

    /**
     * 저장 일시가 [from, to)인 기록 전부(무효 포함), 저장 순. 커밋 뒤 수신자의 일 단위 보정(reward.RewardReconcileService,
     * crew.CrewRunLinkReconcileService, running.TierReconcileService)이 빠진 처리를 찾을 때 쓴다(D-107,
     * D-108).
     */
    public List<SavedRun> savedBetween(LocalDateTime from, LocalDateTime to) {
        return runRecordRepository.savedBetween(from, to);
    }

    /**
     * 저장 일시 &lt; cutoff인 유효 기록의 누적 거리(avatar.BackgroundService.backfillReached가 배포 전에 넘은 랜드마크만 알림
     * 없이 채울 때 쓰는 좁은 조회, D-176).
     */
    public Distance validDistanceBefore(long memberId, LocalDateTime cutoff) {
        return runRecordRepository.validDistanceSavedBefore(memberId, cutoff);
    }

    /** 저장 일시가 [from, to)인 기록이 있는 회원 ID(avatar.BackgroundService.reconcile용 좁은 조회, D-139). */
    public Set<Long> memberIdsSavedBetween(LocalDateTime from, LocalDateTime to) {
        Set<Long> ids = new LinkedHashSet<>();
        runRecordRepository.savedBetween(from, to).forEach(r -> ids.add(r.memberId()));
        return ids;
    }

    /** 누적 유효 거리(avatar.BackgroundService의 배경 해금 판정용 좁은 조회, D-139). */
    public Distance validDistanceOf(long memberId) {
        return runRecordRepository.totals(memberId).distance();
    }

    /**
     * 그 회원의 유효 기록 중 유효 러닝 순번 &gt; ordinal인 것, 저장 일시·기록 ID 순(D-108). TierReconcileService가 티어에 빠진
     * 순번을 넘길 때 쓴다.
     */
    public List<ValidRunScore> validRunsAfter(long memberId, int ordinal) {
        return runRecordRepository.validRunsAfter(memberId, ordinal);
    }

    /** 그 회원의 유효 러닝 수(런바디 요약 G-14의 회차). */
    public int validRunCount(long memberId) {
        return runRecordRepository.countValid(memberId);
    }

    /**
     * 유효 러닝 순번 fromOrdinal~toOrdinal(포함)의 비교용 값, 순번 순(런바디 요약 "최근 10회 변화", G-14). 순번은
     * validRunsAfter와 같다.
     */
    public List<ValidRunStat> validRunStats(long memberId, int fromOrdinal, int toOrdinal) {
        return runRecordRepository.validRunStats(memberId, fromOrdinal, toOrdinal);
    }

    /** ownerIds 회원의 유효 기록 중 거리 ≥ d이고 경로가 남은 것(battle.GhostMatcher, A-07). */
    public List<OwnedRun> validRunsReaching(StandardDistance d, Collection<Long> ownerIds) {
        if (ownerIds.isEmpty()) {
            return List.of();
        }
        return runRecordRepository.validRunsReaching(d.distance(), ownerIds);
    }

    /**
     * 1초 간격 (경과 시간, 누적 거리) 표본을 cut 거리까지. 좌표가 없다. 경로가 파기되었으면 ROUTE_PURGED. 정지가 있는 자유런은 운동 시간 축으로 바꿔
     * 정지 구간을 잘라 낸다(총괄 결정 7-4).
     */
    public RunCurve curve(String recordId, Distance cut) {
        RunRecord record = load(recordId);
        if (record.isRoutePurged()) {
            throw new BusinessRuleException(ReasonCode.ROUTE_PURGED);
        }
        return curveOf(record, cut);
    }

    /**
     * curve와 같은 표본을 예외 없이 준다. 기록이 없거나 경로가 파기되었으면 비어 있다. 읽기 트랜잭션에 참여한 호출에서 업무 예외를 잡으면 트랜잭션이
     * rollback-only가 되므로, 건너뛸 기록은 이것으로 거른다(워치 상대 기록 미리 받기 D-377, 코드 리뷰 12차 BE12-5).
     */
    public Optional<RunCurve> curveOf(String recordId, Distance cut) {
        return runRecordRepository
                .findById(recordId)
                .filter(r -> !r.isRoutePurged())
                .map(r -> curveOf(r, cut));
    }

    private static RunCurve curveOf(RunRecord record, Distance cut) {
        RunTrack track = record.track();
        // 정지 구간과 제외 구간(초과 시속 쌍, FR-047 v0.10)을 같이 잘라 낸다
        List<PauseInterval> stops = record.stops();
        Duration end = track.timeToReach(cut, stops).orElse(record.getElapsed());
        List<CurvePoint> samples =
                track.curve(CURVE_STEP, end, stops).stream()
                        .map(p -> new CurvePoint(p.elapsed(), min(p.distance(), cut)))
                        .toList();
        return new RunCurve(samples, cut);
    }

    /**
     * 런바디 분석 요청 재료(설계 05-19 2.2, D-444): 심박 표본, 위치 점, 정지 구간, 거리, 운동 시간, 시작 기기, 시작·종료 일시. 기록이 없으면 비어
     * 있다(읽기 트랜잭션을 rollback-only로 만들지 않게 예외 대신 비움). 파기한 경로·심박은 빈 목록이다.
     */
    public Optional<RunBodyInput> bodyInputOf(String recordId) {
        return runRecordRepository
                .findById(recordId)
                .map(
                        r ->
                                new RunBodyInput(
                                        r.getRecordId(),
                                        r.getMemberId(),
                                        r.getStartedAt(),
                                        r.endedAt(),
                                        r.getDistance(),
                                        r.getElapsed(),
                                        r.getStartDevice(),
                                        r.isValid(),
                                        r.heartRateSamples(),
                                        r.isRoutePurged() ? List.of() : r.track().points(),
                                        r.pauses(),
                                        r.motionSamples()));
    }

    /** d에 처음 닿은 경과 시간(크루런 도착 시각, 대결 검증). 도착이라 0.5m 반올림 차이를 허용한다(RunRecord.timeToArrive, W54). */
    public Optional<Duration> timeToReach(String recordId, Distance d) {
        return load(recordId).timeToArrive(d);
    }

    /** 경로 좌표. 파기 뒤 빈 목록. */
    public List<GeoPoint> routePoints(String recordId) {
        return load(recordId).routePoints();
    }

    public Optional<BestEntry> best(long memberId, StandardDistance d) {
        return personalRecordBookRepository.findOrCreate(memberId).best(d);
    }

    /** 최장 거리 기록(analysis 성장 분석). */
    public Optional<BestEntry> longest(long memberId) {
        return personalRecordBookRepository.findOrCreate(memberId).longest();
    }

    /** 최장 거리 값만(profile, R7). */
    public Optional<Distance> longestDistance(long memberId) {
        return longest(memberId).flatMap(BestEntry::distance);
    }

    private RunRecord load(String recordId) {
        return runRecordRepository
                .findById(recordId)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.RECORD_NOT_FOUND));
    }

    private static Distance min(Distance a, Distance b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
