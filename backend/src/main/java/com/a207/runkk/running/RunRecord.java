package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.StandardDistance;
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

    /** 신기록 "종류;거리;이전 값" 줄 목록. */
    @Column(name = "new_records", nullable = false, columnDefinition = "text")
    private String newRecordsData;

    @Transient private RunTrack track;

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
        this.track = new RunTrack(startedAt, upload.points(), upload.unstableSegments(), false);
        this.track.sortByTime();
        this.unstableSegmentsData = encodeSegments(upload.unstableSegments());
        storeTrack();
        if (!upload.heartRates().isEmpty()) {
            this.heartRates = HeartRateSeries.of(startedAt, upload.heartRates());
            storeHeartRates();
        }
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

    /** 유효 판정. 처음 한 번만(두 번째는 IllegalStateException, FR-043 재계산 금지). 유효면 점수 확정. */
    public boolean judgeValidity(ValidityRule rule) {
        if (judged) {
            throw new IllegalStateException("이미 판정한 기록");
        }
        this.judged = true;
        this.valid =
                rule.isSatisfiedBy(
                                getDistance(),
                                getElapsed(),
                                getAveragePace(),
                                track().maxSegmentSpeedKmh())
                        && rule.isBackedBy(
                                getDistance(),
                                track().pathLength(),
                                getElapsed(),
                                Duration.between(startedAt, endedAt));
        this.score = valid ? RunningScore.of(getDistance(), getElapsed()).value() : null;
        return valid;
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

    /** d에 처음 닿은 경과 시간. 파기된 경로면 비어 있음. */
    public Optional<Duration> timeToReach(Distance d) {
        return track().timeToReach(d);
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

    public String getRecordId() {
        return recordId;
    }

    public long getMemberId() {
        return memberId;
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

    List<UnstableSegment> unstableSegments() {
        return decodeSegments(unstableSegmentsData);
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

    private static List<NewRecordMark> decodeMarks(String data) {
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
