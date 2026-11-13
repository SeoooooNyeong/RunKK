package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 서버에 저장된 러닝 한 회의 측정 결과(설계 6.1.3). 위치 포인트와 심박은 압축 바이너리 열에 두고 처음 쓸 때 RunTrack·HeartRateSeries로 푼다.
 * 목록 조회는 이 열을 읽지 않는 투영으로 한다(6.5).
 */
@Entity
@Table(name = "run_record")
public class RunRecord {

    private static final Pattern UUID_FORMAT =
            Pattern.compile(
                    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final int KM = 1_000;

    @Id
    @Column(name = "record_id", length = 36)
    private String recordId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private long memberId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at", nullable = false, updatable = false)
    private LocalDateTime endedAt;

    @Column(name = "distance_meters", nullable = false, updatable = false)
    private long distanceMeters;

    @Column(name = "elapsed_millis", nullable = false, updatable = false)
    private long elapsedMillis;

    @Column(name = "average_pace_seconds", nullable = false, updatable = false)
    private int averagePaceSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, updatable = false, length = 10)
    private RunMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_kind", nullable = false, updatable = false, length = 12)
    private CardKind cardKind;

    @Column(name = "battle_id", updatable = false)
    private Long battleId;

    @Column(name = "crew_run_post_id", updatable = false)
    private Long crewRunPostId;

    /** 기록을 시작한 기기(W23, D-378). V33 이전 기록은 PHONE이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "start_device", nullable = false, updatable = false, length = 5)
    private StartDevice startDevice = StartDevice.PHONE;

    @Column(name = "valid", nullable = false)
    private boolean valid;

    @Column(name = "judged", nullable = false)
    private boolean judged;

    @Column(name = "score")
    private Double score;

    @Column(name = "weather_temperature")
    private Integer weatherTemperature;

    @Enumerated(EnumType.STRING)
    @Column(name = "weather_sky", length = 10)
    private SkyCondition weatherSky;

    @Column(name = "point_count", nullable = false)
    private int pointCount;

    @Column(name = "route_data", columnDefinition = "bytea")
    private byte[] routeData;

    @Column(name = "route_purged", nullable = false)
    private boolean routePurged;

    /** 불안정 구간 "시작;끝;미터" 줄 목록. */
    @Column(name = "unstable_segments", nullable = false, columnDefinition = "text")
    private String unstableSegmentsData;

    @Column(name = "heart_rate_data", columnDefinition = "bytea")
    private byte[] heartRateData;

    @Column(name = "heart_rate_purged", nullable = false)
    private boolean heartRatePurged;

    @Column(name = "heart_rate_avg")
    private Integer heartRateAvg;

    @Column(name = "heart_rate_max")
    private Integer heartRateMax;

    /**
     * 기기가 잰 속도·케이던스 표본(MotionSamples 압축 바이너리, V41, S15P31A207-242). 없거나 옛 앱 기록이면 null. 경로를 파기하면 함께
     * 지운다(속도는 위치에서 나온 값이다).
     */
    @Column(name = "motion_data", columnDefinition = "bytea")
    private byte[] motionData;

    /** 신기록 "종류;거리;이전 값" 줄 목록. */
    @Column(name = "new_records", nullable = false, columnDefinition = "text")
    private String newRecordsData;

    /** 서버 저장 일시(RunRecordSaved.occurredAt과 같다). 커밋 뒤 수신자 보정 배치가 이 날짜로 원래 사건일을 안다. */
    @Column(name = "saved_at", nullable = false, updatable = false)
    private LocalDateTime savedAt;

    /** 정지 구간 "시작;끝" 줄 목록(자유런·크루런만, V21·V43). */
    @Column(name = "pauses", nullable = false, updatable = false, columnDefinition = "text")
    private String pausesData;

    /** 정지 구간 길이의 합(ms). 대결이면 0(ck_run_record_pause, V21·V43). */
    @Column(name = "paused_millis", nullable = false, updatable = false)
    private long pausedMillis;

    /**
     * 티어에 반영했는가(유효 기록만 뜻이 있다, 총괄 결정 7-3, V21). 엔티티 저장으로는 바꾸지 않고(updatable = false) 저장소의 조건부 갱신
     * markTierApplied로만 세운다. 다른 트랜잭션이 읽어 둔 엔티티를 저장할 때 표시가 되돌아가지 않게 하려는 것이다.
     */
    @Column(name = "tier_applied", nullable = false, updatable = false)
    private boolean tierApplied;

    /**
     * 유효 판정에서 뺀 거리(m, FR-047 v0.10): 이웃 포인트 사이 시속이 25km/h를 넘는 쌍의 거리 합(서버가 경로로 잰 값).
     * distance_meters는 뺀 뒤 남은 집계 거리다. 원래 측정 거리는 대략 둘의 합이다(V27).
     */
    @Column(name = "excluded_meters", nullable = false, updatable = false)
    private long excludedMeters;

    /** 유효 판정에서 뺀 시간(ms, FR-047 v0.10). elapsed_millis는 뺀 뒤 남은 운동 시간이다(V27). */
    @Column(name = "excluded_millis", nullable = false, updatable = false)
    private long excludedMillis;

    /**
     * v0.10 일회성 재판정 표시(V28, RunRejudgeService). V28이 그때까지 저장된 무효 기록에 PENDING을 세우고, 재판정이 VALID(유효로
     * 바뀌어 부수 처리 대기)·DONE·SKIPPED(경로 파기)로 바꾼다. 새로 받은 기록은 null이다. 저장소의 조건부 갱신으로만 바꾼다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rejudge_state", length = 8, insertable = false, updatable = false)
    private RejudgeState rejudgeState;

    /** 목록 썸네일 경로 모양(RouteShape.encode, 경로 없으면 null, v0.14 D-234). 되채우기 작업이 기존 기록에 채운다. */
    @Column(name = "route_shape", columnDefinition = "text")
    private String routeShape;

    /**
     * 이 기록이 일으킨 티어 평가(v0.14 D-291·D-294). 종류·티어·유효 러닝 순번은 TierApplier가 반영 표시와 같은 조건부 갱신으로, Runimal
     * 확정 유형은 RunEvaluationNote가 한 열 갱신으로 쓴다. 엔티티 저장으로는 바꾸지 않는다.
     */
    @Column(name = "evaluation_kind", length = 20, insertable = false, updatable = false)
    private String evaluationKind;

    @Column(name = "evaluation_tier", length = 10, insertable = false, updatable = false)
    private String evaluationTier;

    @Column(name = "evaluation_valid_runs", insertable = false, updatable = false)
    private Integer evaluationValidRuns;

    @Column(name = "evaluation_runimal_type", length = 20, insertable = false, updatable = false)
    private String evaluationRunimalType;

    @Transient private RunTrack track;

    @Transient private List<PauseInterval> pauses;

    /** 정지 구간과 제외 구간(초과 시속 쌍)을 합친 시간순 목록. 처음 쓸 때 경로로 계산한다. */
    @Transient private List<PauseInterval> stops;

    @Transient private HeartRateSeries heartRates;

    protected RunRecord() {}

    private RunRecord(long memberId, RunUpload upload) {
        this.recordId = upload.recordId();
        this.memberId = memberId;
        this.startedAt = upload.startedAt();
        this.endedAt = upload.endedAt();
        this.distanceMeters = upload.distance().meters();
        this.elapsedMillis = upload.elapsed().toMillis();
        this.averagePaceSeconds =
                (int) Math.max(1, Math.round(elapsedMillis / (double) distanceMeters));
        this.mode = upload.mode();
        this.cardKind = upload.cardKind();
        this.battleId = upload.battleId();
        this.crewRunPostId = upload.crewRunPostId();
        this.startDevice = upload.startDevice();
        this.track = new RunTrack(startedAt, upload.points(), upload.unstableSegments(), false);
        this.track.sortByTime();
        this.unstableSegmentsData = encodeSegments(upload.unstableSegments());
        this.pauses = upload.pauses();
        this.pausesData = encodePauses(pauses);
        this.pausedMillis = pauses.stream().mapToLong(p -> p.duration().toMillis()).sum();
        storeTrack();
        RouteShape shape = RouteShape.of(routePoints());
        this.routeShape = shape.isEmpty() ? null : shape.encode();
        if (!upload.heartRates().isEmpty()) {
            this.heartRates = HeartRateSeries.of(startedAt, upload.heartRates());
            storeHeartRates();
        }
        this.motionData =
                MotionSamples.encode(startedAt, MotionSamples.normalize(upload.motions()));
        this.newRecordsData = "";
    }

    /**
     * 수신 기록 생성. 포인트 수 = 선언 수(NFR-010), 시작 &lt; 종료, 거리·시간 &gt; 0, 모드·카드 종류·대결·크루런 ID 일관성. 위반이면
     * IllegalArgumentException. 포인트는 측정 일시 순으로 정렬한다.
     */
    public static RunRecord receive(long memberId, RunUpload upload) {
        require(
                upload.recordId() != null && UUID_FORMAT.matcher(upload.recordId()).matches(),
                "기록 ID는 UUID다");
        require(hasOrderedTimes(upload), "시작 일시는 종료 일시보다 앞이다");
        require(hasPositiveAmounts(upload), "거리·시간은 0보다 크다");
        require(upload.points().size() == upload.declaredPointCount(), "포인트 수가 선언과 다르다");
        requireConsistentMode(upload);
        return new RunRecord(memberId, upload);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean hasOrderedTimes(RunUpload upload) {
        return upload.startedAt() != null
                && upload.endedAt() != null
                && upload.startedAt().isBefore(upload.endedAt());
    }

    private static boolean hasPositiveAmounts(RunUpload upload) {
        return upload.distance() != null
                && upload.distance().meters() > 0
                && upload.elapsed() != null
                && upload.elapsed().isPositive();
    }

    /**
     * 유효 판정(A-03, D-94, FR-047 v0.10). 처음 한 번만(두 번째는 IllegalStateException, FR-043 재계산 금지). 판정 규칙은
     * judge가 정한다. 유효면 점수 확정. 경로 파기 뒤 다시 판정하지 않는다.
     */
    public boolean judgeValidity(ValidityRule rule) {
        if (judged) {
            throw new IllegalStateException("이미 판정한 기록");
        }
        this.judged = true;
        judge(rule);
        return valid;
    }

    /**
     * v0.10 규칙 변경에 따른 일회성 재판정(사용자 결정, RunRejudgeService만 부른다). FR-043의 재계산 금지와 judgeValidity의 한 번만
     * 판정은 그대로 두고, 이 경로만 좁게 연다: 이미 판정했고, 무효이며, 경로가 남은 기록만 받는다(그 밖이면 IllegalStateException). 유효 기록은 옛
     * 규칙에서 25km/h를 넘는 쌍이 없었으므로 새 규칙으로도 값이 같아 다시 보지 않는다. 저장된 거리·시간을 측정값으로 보고 judge를 다시 적용한다.
     */
    boolean rejudgeInvalid(ValidityRule rule) {
        if (!judged || valid || !hasRoute()) {
            throw new IllegalStateException("재판정할 수 없는 기록");
        }
        this.stops = null;
        judge(rule);
        return valid;
    }

    /**
     * 판정 규칙(FR-047 v0.10). 이웃 포인트 사이 시속이 상한을 넘는 쌍(정지 경계 쌍·불안정 끝 쌍 제외)의 거리와 시간을 뺀다. 뺀 거리는 측정 거리와 경로로
     * 다시 센 집계 거리(countedLength) 가운데 작은 값이다: 옛 앱은 그 쌍을 더해 보내고 새 앱은 빼고 보내므로 어느 쪽이든 두 번 빼지 않는다. 뺀 시간은
     * 서버가 경로로 잰 값을 운동 시간에서 뺀다(앱은 시간을 빼지 않는다). 남은 거리·시간·평균 페이스에 세 조건을 적용하고, 다섯째 조건 isBackedBy는 남은
     * 거리를 초과 쌍을 뺀 경로 길이와 대조한다(wall = 종료 − 시작 − 정지 합, 선언 시간과 비교). 남은 거리·시간이 0이면 무효이고 저장 값은 측정값 그대로
     * 둔다(거리·시간 &gt; 0 제약). 그렇지 않으면 저장 거리·시간·페이스를 남은 값으로 바꾼다.
     */
    private void judge(ValidityRule rule) {
        List<PauseInterval> pauses = pauses();
        ExcludedSegments excluded = track().overspeedSegments(pauses, rule);
        Distance counted = getDistance();
        Duration countedElapsed = getElapsed();
        if (!excluded.isEmpty()) {
            Distance byTrack = track().countedLength(excluded.withPauses(pauses));
            counted = byTrack.compareTo(counted) < 0 ? byTrack : counted;
            countedElapsed = countedElapsed.minus(excluded.elapsed());
        }
        this.excludedMeters = excluded.distance().meters();
        this.excludedMillis = excluded.elapsed().toMillis();
        if (counted.meters() <= 0 || !countedElapsed.isPositive()) {
            this.valid = false;
            this.score = null;
            return;
        }
        Duration wall = Duration.between(startedAt, endedAt).minus(pausedTotal());
        Distance path = track().pathLength(pauses).minus(excluded.distance());
        Duration declaredElapsed = getElapsed();
        this.distanceMeters = counted.meters();
        this.elapsedMillis = countedElapsed.toMillis();
        this.averagePaceSeconds =
                (int) Math.max(1, Math.round(elapsedMillis / (double) distanceMeters));
        this.valid =
                rule.isSatisfiedBy(getDistance(), getElapsed(), getAveragePace())
                        && rule.isBackedBy(getDistance(), path, declaredElapsed, wall);
        this.score = valid ? RunningScore.of(getDistance(), getElapsed()).value() : null;
    }

    /** 유효 판정에서 뺀 거리(FR-047 v0.10). 뺀 구간이 없으면 0. */
    public Distance getExcludedDistance() {
        return Distance.ofMeters(excludedMeters);
    }

    /** 유효 판정에서 뺀 시간(FR-047 v0.10). 뺀 구간이 없으면 0. */
    public Duration getExcludedElapsed() {
        return Duration.ofMillis(excludedMillis);
    }

    /** 날씨 붙이기. null이면 무시. */
    public void attachWeather(Weather w) {
        if (w == null) {
            return;
        }
        this.weatherTemperature = w.temperatureC();
        this.weatherSky = w.sky();
    }

    /** 늦게 온 심박 덧붙이기. 유효 여부·점수는 바꾸지 않는다(FR-043). 심박 동의 확인은 서비스가 한다. */
    public void appendHeartRates(List<HeartRateSample> samples) {
        if (samples.isEmpty()) {
            return;
        }
        HeartRateSeries series = heartRateSeries();
        if (series == null) {
            this.heartRates = HeartRateSeries.of(startedAt, samples);
        } else {
            series.append(samples);
        }
        storeHeartRates();
    }

    /** 신기록 표시. 무효 러닝이면 무시한다(FR-186). */
    public void markNewRecords(List<NewRecordMark> marks) {
        if (!valid) {
            return;
        }
        this.newRecordsData = encodeMarks(marks);
    }

    /** 경로 파기. 거리·시간·페이스는 남긴다(FR-026). */
    public void purgeRoute() {
        track().purge();
        this.routeData = null;
        this.routePurged = true;
        this.motionData = null;
    }

    /** 심박 원자료 파기. 요약값(평균·최고)은 남긴다(FR-029). */
    public void purgeHeartRates() {
        HeartRateSeries series = heartRateSeries();
        if (series != null) {
            series.purge();
        }
        this.heartRateData = null;
        this.heartRatePurged = true;
    }

    /**
     * d에 처음 닿은 경과 시간(정지 시간을 뺀다, D-122·D-163). 제외 구간(초과 시속 쌍)도 정지처럼 거리와 시간에서 뺀다(FR-047 v0.10). 파기된
     * 경로면 비어 있음.
     */
    public Optional<Duration> timeToReach(Distance d) {
        return track().timeToReach(d, stops());
    }

    /**
     * 대결·크루런 도착 시간(정지·제외 구간을 뺌, 0.5m 반올림 허용, W54). 신기록·구간에는 {@link #timeToReach(Distance)}를
     * 쓴다(PL5-B1).
     */
    public Optional<Duration> timeToArrive(Distance d) {
        return track().timeToArrive(d, stops());
    }

    /**
     * 정지 구간과 제외 구간(초과 시속 쌍, ValidityRule.STANDARD)을 합친 시간순 목록. 도달 시간·곡선이 둘을 같이 건너뛴다. 옛 규칙으로 유효였던
     * 기록은 초과 쌍이 없어 정지 구간과 같다. 파기된 경로면 정지 구간만이다.
     */
    List<PauseInterval> stops() {
        if (stops == null) {
            this.stops =
                    track().overspeedSegments(pauses(), ValidityRule.STANDARD).withPauses(pauses());
        }
        return stops;
    }

    /** 정지 구간(읽기 전용, 시간순). 정지가 없으면 빈 목록(FR-036, FR-194). */
    public List<PauseInterval> pauses() {
        if (pauses == null) {
            this.pauses = decodePauses(pausesData);
        }
        return pauses;
    }

    /** 정지 시간 합. 정지가 없으면 0(D-94). */
    public Duration pausedTotal() {
        return Duration.ofMillis(pausedMillis);
    }

    /** 1km 구간 페이스. 경로가 없으면 빈 목록. */
    public List<Pace> splitPaces() {
        List<Pace> paces = new ArrayList<>();
        Duration previous = Duration.ZERO;
        for (int km = 1; km * (long) KM <= distanceMeters; km++) {
            Optional<Duration> reached = timeToReach(Distance.ofMeters((long) km * KM));
            if (reached.isEmpty()) {
                break;
            }
            Duration split = reached.get().minus(previous);
            if (split.isNegative() || split.isZero()) {
                break;
            }
            paces.add(Pace.of(split, Distance.ofMeters(KM)));
            previous = reached.get();
        }
        return paces;
    }

    /** 가장 빠른 1km(splitPaces 최소). */
    public Optional<Pace> fastestKm() {
        return splitPaces().stream().min(Comparator.comparingInt(Pace::secondsPerKm));
    }

    /** 심박 구간 분포(FR-185). 심박이 없으면 빈 맵. */
    public Map<HeartRateZone, Duration> heartRateZones(int maxHeartRate) {
        HeartRateSeries series = heartRateSeries();
        return series == null ? Map.of() : series.zoneDistribution(maxHeartRate);
    }

    /** 기록상 거리가 d에 닿았는가(대결 검증, FR-092). */
    public boolean reached(Distance d) {
        return getDistance().isAtLeast(d);
    }

    /** 저장 직전에 한 번 부른다(RunUploadService.store). */
    public void markSaved(LocalDateTime at) {
        if (savedAt != null) {
            throw new IllegalStateException("이미 저장된 기록");
        }
        this.savedAt = at;
    }

    /**
     * 삭제할 수 있는 기록인가(FR-225, v0.14 D-231): 집계 제외(무효)이고 일회성 재판정이 아직 끝나지 않은(PENDING) 기록이나 유효로 바뀌는
     * 중인(VALID) 기록이 아니다.
     */
    public boolean isDeletable() {
        return !valid && rejudgeState != RejudgeState.PENDING && rejudgeState != RejudgeState.VALID;
    }

    /** 목록 썸네일 모양(없으면 비어 있음, D-234). */
    public Optional<RouteShape> getRouteShape() {
        return routeShape == null ? Optional.empty() : Optional.of(RouteShape.decode(routeShape));
    }

    /** 경로 모양을 붙인다(되채우기용, RouteShapeBackfillService). 빈 모양이면 붙이지 않는다. */
    public void attachRouteShape(RouteShape shape) {
        if (shape != null && !shape.isEmpty()) {
            this.routeShape = shape.encode();
        }
    }

    /** 이 기록이 일으킨 평가(없으면 비어 있음, D-291). */
    Optional<EvaluationView> evaluation() {
        if (evaluationKind == null) {
            return Optional.empty();
        }
        return Optional.of(
                new EvaluationView(
                        evaluationKind,
                        evaluationTier == null ? null : Tier.valueOf(evaluationTier),
                        evaluationValidRuns == null ? 0 : evaluationValidRuns,
                        evaluationRunimalType));
    }

    /** 메모리 위 엔티티의 평가 표시를 쓴다. 저장소 구현이 조건부 갱신과 함께 부른다(시험의 인메모리 저장소). */
    void markEvaluation(String kind, String tier, int validRuns) {
        this.evaluationKind = kind;
        this.evaluationTier = tier;
        this.evaluationValidRuns = validRuns;
    }

    /** 메모리 위 엔티티의 Runimal 확정 유형을 쓴다(시험의 인메모리 저장소). */
    void markEvaluationRunimalType(String type) {
        this.evaluationRunimalType = type;
    }

    /** 티어에 반영했는가(총괄 결정 7-3). */
    public boolean isTierApplied() {
        return tierApplied;
    }

    /** 메모리 위 엔티티의 표시를 세운다. 처음이면 true. 저장소 구현이 조건부 갱신과 함께 부른다. */
    boolean markTierApplied() {
        if (tierApplied) {
            return false;
        }
        this.tierApplied = true;
        return true;
    }

    public LocalDateTime getSavedAt() {
        return savedAt;
    }

    public String getRecordId() {
        return recordId;
    }

    public StartDevice getStartDevice() {
        return startDevice;
    }

    public long getMemberId() {
        return memberId;
    }

    /** 종료 일시(런바디 요청, D-444). */
    LocalDateTime endedAt() {
        return endedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public Distance getDistance() {
        return Distance.ofMeters(distanceMeters);
    }

    public Duration getElapsed() {
        return Duration.ofMillis(elapsedMillis);
    }

    public Pace getAveragePace() {
        return new Pace(averagePaceSeconds);
    }

    public boolean isValid() {
        return valid;
    }

    /** 러닝 점수. 무효면 null. */
    public RunningScore getScore() {
        return score == null ? null : new RunningScore(score);
    }

    public RunMode getMode() {
        return mode;
    }

    public CardKind getCardKind() {
        return cardKind;
    }

    /** 날씨. 없으면 null. */
    public Weather getWeather() {
        return weatherSky == null ? null : new Weather(weatherTemperature, weatherSky);
    }

    public List<NewRecordMark> getNewRecords() {
        return decodeMarks(newRecordsData);
    }

    /** 경로 좌표(읽기 전용). 파기 뒤 빈 목록. */
    public List<GeoPoint> routePoints() {
        return track().points().stream().map(LocationPoint::point).toList();
    }

    Long battleId() {
        return battleId;
    }

    Long crewRunPostId() {
        return crewRunPostId;
    }

    boolean hasRoute() {
        return !routePurged && pointCount > 0;
    }

    boolean isRoutePurged() {
        return routePurged;
    }

    /** v0.10 일회성 재판정 표시(V28). 재판정 대상이 아니었으면 null. */
    RejudgeState rejudgeState() {
        return rejudgeState;
    }

    /** 메모리 위 엔티티의 재판정 표시를 바꾼다. 저장소 구현이 조건부 갱신과 함께 부른다(시험은 V28 표시를 흉내 낼 때). */
    void markRejudgeState(RejudgeState state) {
        this.rejudgeState = state;
    }

    List<UnstableSegment> unstableSegments() {
        return decodeSegments(unstableSegmentsData);
    }

    /** 심박 원자료(시간순). 없거나 파기했으면 빈 목록(런바디 요청, D-444). */
    List<HeartRateSample> heartRateSamples() {
        HeartRateSeries series = heartRateSeries();
        return series == null ? List.of() : series.samples();
    }

    /** 기기가 잰 속도·케이던스 표본(시간순). 없거나 경로를 파기했으면 빈 목록(런바디 요청, S15P31A207-242). */
    List<MotionSample> motionSamples() {
        return MotionSamples.decode(startedAt, motionData);
    }

    HeartRateSummary heartRateSummary() {
        return heartRateMax == null ? null : new HeartRateSummary(heartRateAvg, heartRateMax);
    }

    RunTrack track() {
        if (track == null) {
            this.track =
                    new RunTrack(
                            startedAt,
                            RunTrack.decodePoints(startedAt, routeData),
                            unstableSegments(),
                            routePurged);
        }
        return track;
    }

    RunSummary summary() {
        return new RunSummary(
                recordId,
                memberId,
                startedAt,
                getDistance(),
                getElapsed(),
                getAveragePace(),
                valid,
                score,
                mode,
                cardKind,
                battleId,
                crewRunPostId);
    }

    private HeartRateSeries heartRateSeries() {
        if (heartRates == null && (heartRateData != null || heartRatePurged)) {
            this.heartRates =
                    new HeartRateSeries(
                            startedAt,
                            HeartRateSeries.decodeSamples(startedAt, heartRateData),
                            heartRatePurged,
                            heartRateSummary());
        }
        return heartRates;
    }

    private void storeTrack() {
        this.pointCount = track.points().size();
        this.routeData = pointCount == 0 ? null : track.encodePoints();
    }

    private void storeHeartRates() {
        this.heartRateData = heartRates.encodeSamples();
        HeartRateSummary summary = heartRates.summary();
        if (summary != null) {
            this.heartRateAvg = summary.average();
            this.heartRateMax = summary.max();
        }
    }

    /** 모드 일관성 검사(6.4에서 남긴 RunMode 분기 1곳). */
    private static void requireConsistentMode(RunUpload upload) {
        if (upload.mode() == null
                || upload.cardKind() == null
                || !upload.cardKind().belongsTo(upload.mode())) {
            throw new IllegalArgumentException("모드와 카드 종류가 맞지 않는다");
        }
        boolean battle = upload.mode() == RunMode.BATTLE;
        boolean crewRun = upload.mode() == RunMode.CREW_RUN;
        if (battle != (upload.battleId() != null) || crewRun != (upload.crewRunPostId() != null)) {
            throw new IllegalArgumentException("대결 ID는 대결에만, 크루런 게시글 ID는 크루런에만");
        }
    }

    private static String encodeSegments(List<UnstableSegment> segments) {
        return segments.stream()
                .map(s -> s.from() + ";" + s.to() + ";" + s.distance().meters())
                .collect(Collectors.joining("\n"));
    }

    private static List<UnstableSegment> decodeSegments(String data) {
        if (data == null || data.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(data.split("\n"))
                .map(line -> line.split(";"))
                .map(
                        f ->
                                new UnstableSegment(
                                        LocalDateTime.parse(f[0]),
                                        LocalDateTime.parse(f[1]),
                                        Distance.ofMeters(Long.parseLong(f[2]))))
                .toList();
    }

    private static String encodePauses(List<PauseInterval> pauses) {
        return pauses.stream().map(p -> p.from() + ";" + p.to()).collect(Collectors.joining("\n"));
    }

    private static List<PauseInterval> decodePauses(String data) {
        if (data == null || data.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(data.split("\n"))
                .map(line -> line.split(";"))
                .map(f -> new PauseInterval(LocalDateTime.parse(f[0]), LocalDateTime.parse(f[1])))
                .toList();
    }

    private static String encodeMarks(List<NewRecordMark> marks) {
        return marks.stream()
                .map(
                        m ->
                                m.kind()
                                        + ";"
                                        + (m.distance() == null ? "" : m.distance().name())
                                        + ";"
                                        + m.previous())
                .collect(Collectors.joining("\n"));
    }

    static List<NewRecordMark> decodeMarks(String data) {
        if (data == null || data.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(data.split("\n"))
                .map(line -> line.split(";", 3))
                .map(
                        f ->
                                new NewRecordMark(
                                        NewRecordMark.Kind.valueOf(f[0]),
                                        f[1].isEmpty() ? null : StandardDistance.valueOf(f[1]),
                                        f[2]))
                .toList();
    }
}
