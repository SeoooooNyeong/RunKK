package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.GeoPoint;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 러닝 한 회의 위치 포인트 목록과 불안정 구간(설계 6.1.3, A-03). 거리·시간 계산에서 UNSTABLE 포인트로 끝나는 구간(신호 공백을 건너뛴 직선)은 뺀다.
 * 포인트는 압축 바이너리로 저장한다(6.5, 부록 E E-11 제안).
 */
public class RunTrack {

    private static final double MILLIS_PER_SECOND = 1000.0;
    private static final double MPS_TO_KMH = 3.6;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    /** 부동소수 합의 오차로 정확히 그 거리에서 끝나는 기록이 닿지 못하는 일을 막는다(1mm). */
    private static final double FLOAT_TOLERANCE = 0.001;

    private final LocalDateTime startedAt;
    private List<LocationPoint> points;
    private final List<UnstableSegment> unstableSegments;
    private boolean purged;

    RunTrack(
            LocalDateTime startedAt,
            List<LocationPoint> points,
            List<UnstableSegment> unstableSegments,
            boolean purged) {
        this.startedAt = startedAt;
        this.points = new ArrayList<>(points);
        this.unstableSegments = List.copyOf(unstableSegments);
        this.purged = purged;
    }

    /** 측정 일시 순으로 정렬하고 같은 일시의 중복 포인트는 하나만 남긴다. */
    public void sortByTime() {
        List<LocationPoint> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparing(LocationPoint::measuredAt));
        List<LocationPoint> unique = new ArrayList<>();
        for (LocationPoint p : sorted) {
            if (unique.isEmpty()
                    || !unique.get(unique.size() - 1).measuredAt().equals(p.measuredAt())) {
                unique.add(p);
            }
        }
        this.points = unique;
    }

    /** 정지 구간 없이 잰 최고 시속(maxSegmentSpeedKmh(빈 목록), D-94). */
    public double maxSegmentSpeedKmh() {
        return maxSegmentSpeedKmh(List.of());
    }

    /**
     * 이웃 포인트 사이 최고 시속(A-03). v0.10부터 유효 판정은 이 값으로 기록 전체를 무효로 하지 않고 상한을 넘는 쌍만 뺀다(overspeedSegments).
     * 불안정 구간 끝 포인트로 들어가는 쌍(신호 공백을 건너뛴 직선)과 정지 구간에 걸친 쌍 (PauseInterval.straddles, 정지 앞 마지막 포인트 ~ 재개
     * 뒤 첫 포인트)은 뺀다(D-94, D-163).
     */
    public double maxSegmentSpeedKmh(List<PauseInterval> pauses) {
        double max = 0;
        for (int i = 1; i < points.size(); i++) {
            LocationPoint a = points.get(i - 1);
            LocationPoint b = points.get(i);
            if (b.signal() == SignalState.UNSTABLE || crossesPause(a, b, pauses)) {
                continue;
            }
            long millis = Duration.between(a.measuredAt(), b.measuredAt()).toMillis();
            if (millis <= 0) {
                continue;
            }
            double speed = meters(a.point(), b.point()) / (millis / MILLIS_PER_SECOND) * MPS_TO_KMH;
            max = Math.max(max, speed);
        }
        return max;
    }

    /**
     * 이웃 포인트 사이 시속이 rule의 상한을 넘는 쌍(FR-047 v0.10 제외 구간). maxSegmentSpeedKmh와 같은 쌍을 본다: 불안정 구간 끝 포인트로
     * 들어가는 쌍, 정지 구간에 걸친 쌍, 시각 차가 없는 쌍은 보지 않는다. 거리 합은 반올림한 미터다.
     */
    public ExcludedSegments overspeedSegments(List<PauseInterval> pauses, ValidityRule rule) {
        List<PauseInterval> intervals = new ArrayList<>();
        double meters = 0;
        Duration elapsed = Duration.ZERO;
        for (int i = 1; i < points.size(); i++) {
            LocationPoint a = points.get(i - 1);
            LocationPoint b = points.get(i);
            if (b.signal() == SignalState.UNSTABLE || crossesPause(a, b, pauses)) {
                continue;
            }
            long millis = Duration.between(a.measuredAt(), b.measuredAt()).toMillis();
            if (millis <= 0) {
                continue;
            }
            double seg = meters(a.point(), b.point());
            if (rule.isOverspeed(seg / (millis / MILLIS_PER_SECOND) * MPS_TO_KMH)) {
                intervals.add(new PauseInterval(a.measuredAt(), b.measuredAt()));
                meters += seg;
                elapsed = elapsed.plus(Duration.between(a.measuredAt(), b.measuredAt()));
            }
        }
        if (intervals.isEmpty()) {
            return ExcludedSegments.NONE;
        }
        return new ExcludedSegments(intervals, Distance.ofMeters(Math.round(meters)), elapsed);
    }

    /**
     * 집계 거리를 경로로 다시 센 값: 불안정 구간 끝 쌍과 stops(정지 구간·제외 구간)에 걸친 쌍을 뺀 이웃 포인트 거리의 합. 앱 RunSession이 달리는 중에
     * 세는 거리와 같은 쌍을 더한다(FR-047 v0.10).
     */
    public Distance countedLength(List<PauseInterval> stops) {
        double[] cumulative = cumulativeMeters(stops);
        return Distance.ofMeters(
                cumulative.length == 0 ? 0 : Math.round(cumulative[cumulative.length - 1]));
    }

    /** 정지 구간 없이 잰 경로 길이(pathLength(빈 목록), D-94). */
    public Distance pathLength() {
        return pathLength(List.of());
    }

    /**
     * 이웃 포인트 사이 거리의 합(불안정 구간 포함). 정지 구간에 걸친 쌍은 뺀다. 유효 판정 다섯째 조건(isBackedBy)의 경로 길이다(A-03, CR34-08,
     * D-94).
     */
    public Distance pathLength(List<PauseInterval> pauses) {
        double acc = 0;
        for (int i = 1; i < points.size(); i++) {
            LocationPoint a = points.get(i - 1);
            LocationPoint b = points.get(i);
            if (!crossesPause(a, b, pauses)) {
                acc += meters(a.point(), b.point());
            }
        }
        return Distance.ofMeters(Math.round(acc));
    }

    /** 정지 구간 없이 잰 도달 시간(timeToReach(d, 빈 목록)). 대결 상대 트랙(RunCurve)처럼 정지가 없는 기록에 쓴다. */
    public Optional<Duration> timeToReach(Distance d) {
        return timeToReach(d, List.of());
    }

    /**
     * 누적 거리 d에 처음 닿은 경과 시간. 두 포인트 사이는 직선 보간이고 불안정 구간 건너뜀과 정지 구간을 가로지르는 쌍은 거리를 더하지 않는다. 도달 시각까지의 정지
     * 시간을 뺀다(D-122, D-163). 파기했거나 닿지 않았으면 비어 있음.
     */
    public Optional<Duration> timeToReach(Distance d, List<PauseInterval> pauses) {
        if (purged || points.isEmpty()) {
            return Optional.empty();
        }
        double target = d.meters();
        double acc = 0;
        for (int i = 1; i < points.size(); i++) {
            LocationPoint a = points.get(i - 1);
            LocationPoint b = points.get(i);
            if (b.signal() == SignalState.UNSTABLE || crossesPause(a, b, pauses)) {
                continue;
            }
            double seg = meters(a.point(), b.point());
            if (seg > 0 && acc + seg + FLOAT_TOLERANCE >= target) {
                double ratio = Math.min(1.0, (target - acc) / seg);
                long segMillis = Duration.between(a.measuredAt(), b.measuredAt()).toMillis();
                LocalDateTime at =
                        a.measuredAt().plusNanos(Math.round(ratio * segMillis) * 1_000_000L);
                return Optional.of(
                        Duration.between(startedAt, at)
                                .minus(pausedBetween(startedAt, at, pauses)));
            }
            acc += seg;
        }
        return Optional.empty();
    }

    /** 쌍 (a, b)가 어느 정지 구간에 걸치는가(경계 비포함, D-163). */
    private static boolean crossesPause(
            LocationPoint a, LocationPoint b, List<PauseInterval> pauses) {
        for (PauseInterval p : pauses) {
            if (p.straddles(a.measuredAt(), b.measuredAt())) {
                return true;
            }
        }
        return false;
    }

    /** [from, to] 안에 든 정지 시간의 합. */
    private static Duration pausedBetween(
            LocalDateTime from, LocalDateTime to, List<PauseInterval> pauses) {
        Duration total = Duration.ZERO;
        for (PauseInterval p : pauses) {
            LocalDateTime start = p.from().isAfter(from) ? p.from() : from;
            LocalDateTime end = p.to().isBefore(to) ? p.to() : to;
            if (start.isBefore(end)) {
                total = total.plus(Duration.between(start, end));
            }
        }
        return total;
    }

    /** 시작 뒤 t 시점의 누적 거리(고스트 상대 트랙용). */
    public Distance cumulativeDistanceAt(Duration t) {
        LocalDateTime at = startedAt.plus(t);
        double acc = 0;
        for (int i = 1; i < points.size(); i++) {
            LocationPoint a = points.get(i - 1);
            LocationPoint b = points.get(i);
            if (!a.measuredAt().isBefore(at)) {
                break;
            }
            if (b.signal() == SignalState.UNSTABLE) {
                continue;
            }
            double seg = meters(a.point(), b.point());
            if (!b.measuredAt().isAfter(at)) {
                acc += seg;
            } else {
                long segMillis = Duration.between(a.measuredAt(), b.measuredAt()).toMillis();
                long partMillis = Duration.between(a.measuredAt(), at).toMillis();
                acc += seg * partMillis / segMillis;
                break;
            }
        }
        return Distance.ofMeters(Math.round(acc));
    }

    /**
     * 0부터 end까지 step 간격의 누적 거리 표본(RunCurve용). cumulativeDistanceAt을 시점마다 부르면 O(n²)이라 포인트를 한 번 훑으며
     * 만든다. 마지막 표본은 end 시점이다.
     */
    List<CurvePoint> curve(Duration step, Duration end) {
        return curve(step, end, List.of());
    }

    /**
     * 운동 시간 축의 누적 거리 표본(총괄 결정, 설계 쟁점 7-4). 정지 구간을 잘라 내고 이후 포인트의 경과 시간을 그때까지의 정지 합만큼 당기며, 정지에 걸친 쌍은
     * 거리를 더하지 않는다. 그래서 정지가 있는 자유런을 고스트·나와의 싸움 상대로 재생해도 정지 없는 러닝처럼 보인다. end도 운동 시간이다. 정지가 없으면
     * curve(step, end)와 같다.
     */
    List<CurvePoint> curve(Duration step, Duration end, List<PauseInterval> pauses) {
        long[] offsets = offsetsMillis(pauses);
        double[] cumulative = cumulativeMeters(pauses);
        List<CurvePoint> result = new ArrayList<>();
        int i = -1;
        long endMillis = end.toMillis();
        for (long t = 0; ; t = Math.min(t + step.toMillis(), endMillis)) {
            while (i + 1 < offsets.length && offsets[i + 1] <= t) {
                i++;
            }
            double meters = metersAt(t, i, offsets, cumulative);
            result.add(new CurvePoint(Duration.ofMillis(t), Distance.ofMeters(Math.round(meters))));
            if (t >= endMillis) {
                return result;
            }
        }
    }

    /** 포인트마다 시작부터의 운동 시간(ms): 경과에서 그 시각까지의 정지 합을 뺀다. */
    private long[] offsetsMillis(List<PauseInterval> pauses) {
        long[] offsets = new long[points.size()];
        for (int i = 0; i < offsets.length; i++) {
            LocalDateTime at = points.get(i).measuredAt();
            offsets[i] =
                    Duration.between(startedAt, at)
                            .minus(pausedBetween(startedAt, at, pauses))
                            .toMillis();
        }
        return offsets;
    }

    /** 포인트마다 누적 거리(m). 불안정 구간으로 들어가는 구간과 정지에 걸친 구간은 더하지 않는다. */
    private double[] cumulativeMeters(List<PauseInterval> pauses) {
        double[] cumulative = new double[points.size()];
        for (int i = 1; i < cumulative.length; i++) {
            boolean skip =
                    points.get(i).signal() == SignalState.UNSTABLE
                            || crossesPause(points.get(i - 1), points.get(i), pauses);
            cumulative[i] =
                    cumulative[i - 1]
                            + (skip ? 0 : meters(points.get(i - 1).point(), points.get(i).point()));
        }
        return cumulative;
    }

    /** t 시점의 누적 거리. i는 t 이전 마지막 포인트(없으면 -1), 다음 포인트까지 직선 보간(sw-test 복잡도 분리). */
    private static double metersAt(long t, int i, long[] offsets, double[] cumulative) {
        if (i < 0) {
            return 0;
        }
        if (i == offsets.length - 1 || offsets[i + 1] == offsets[i]) {
            return cumulative[i];
        }
        return cumulative[i]
                + (cumulative[i + 1] - cumulative[i])
                        * (t - offsets[i])
                        / (offsets[i + 1] - offsets[i]);
    }

    /** 첫 포인트. 파기 뒤 비어 있음. */
    public Optional<GeoPoint> firstPoint() {
        if (purged || points.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(points.get(0).point());
    }

    /** 포인트를 지운다. 불안정 구간의 시각·거리는 남긴다(FR-026). */
    public void purge() {
        this.points = new ArrayList<>();
        this.purged = true;
    }

    /** 누적 거리 [from, to] 구간에 불안정 구간이 시작하는가(FR-063 표시). 파기 뒤에는 불안정 구간이 하나라도 있으면 참이다. */
    public boolean hasUnstableWithin(Distance from, Distance to) {
        if (unstableSegments.isEmpty()) {
            return false;
        }
        if (purged || points.isEmpty()) {
            return true;
        }
        for (UnstableSegment segment : unstableSegments) {
            Distance at = cumulativeDistanceAt(Duration.between(startedAt, segment.from()));
            if (at.isAtLeast(from) && to.isAtLeast(at)) {
                return true;
            }
        }
        return false;
    }

    public List<LocationPoint> points() {
        return Collections.unmodifiableList(points);
    }

    public List<UnstableSegment> unstableSegments() {
        return unstableSegments;
    }

    public boolean isPurged() {
        return purged;
    }

    /**
     * 하버사인 거리(m, 소수 유지). GeoPoint.distanceTo는 미터 정수로 반올림해 1초 간격 포인트(1~4m)마다 오차가 쌓이므로 트랙 계산은 이 값을
     * 쓴다(D-18과 같은 식, 지구 반지름 6,371,000m).
     */
    static double meters(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h =
                Math.pow(Math.sin(dLat / 2), 2)
                        + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    /** 포인트를 압축 바이너리로 바꾼다(시작 기준 밀리초, 위도, 경도, 정확도, 신호). */
    byte[] encodePoints() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(points.size());
            for (LocationPoint p : points) {
                out.writeLong(Duration.between(startedAt, p.measuredAt()).toMillis());
                out.writeDouble(p.point().latitude());
                out.writeDouble(p.point().longitude());
                out.writeFloat((float) p.accuracyMeters());
                out.writeByte(p.signal().ordinal());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("경로 압축 실패", e);
        }
        return bytes.toByteArray();
    }

    static List<LocationPoint> decodePoints(LocalDateTime startedAt, byte[] data) {
        if (data == null || data.length == 0) {
            return List.of();
        }
        try (DataInputStream in =
                new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(data)))) {
            int size = in.readInt();
            List<LocationPoint> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                LocalDateTime at = startedAt.plusNanos(in.readLong() * 1_000_000L);
                GeoPoint point = new GeoPoint(in.readDouble(), in.readDouble());
                double accuracy = in.readFloat();
                SignalState signal = SignalState.values()[in.readByte()];
                result.add(new LocationPoint(at, point, accuracy, signal));
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("경로 해제 실패", e);
        }
    }
}
