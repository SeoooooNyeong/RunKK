package com.a207.runkk.battle;

import com.a207.runkk.common.Distance;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 경과 시간별 누적 거리 표로 상대 위치를 보간한다(설계 6.1.6, A-08, D-13 직선 보간). 앱 domain.battle.OpponentTrack과 같은 규칙이고
 * 계약 표(contract/opponent-track.csv)로 두 구현의 결과를 맞춘다(14장).
 *
 * <p>규칙: 첫 표본 전은 (0, 0)에서 첫 표본까지 비례, 마지막 표본 뒤는 마지막 거리, 음수 경과는 0. 거리는 미터 반올림, 시간은 밀리초 반올림.
 */
public final class OpponentTrack {

    private static final double MILLIS_PER_SECOND = 1_000.0;
    private static final String SAMPLE_SEPARATOR = ";";
    private static final String FIELD_SEPARATOR = ":";

    private static final OpponentTrack EMPTY = new OpponentTrack(List.of());

    private final List<TrackSample> samples;

    private OpponentTrack(List<TrackSample> samples) {
        this.samples = List.copyOf(samples);
    }

    /** 경과 시간 오름차순, 누적 거리 단조 증가(같아도 됨). 어기면 IllegalArgumentException. */
    public static OpponentTrack of(List<TrackSample> samples) {
        if (!isOrdered(samples)) {
            throw new IllegalArgumentException("경과 시간은 늘고 누적 거리는 줄지 않아야 한다");
        }
        return new OpponentTrack(samples);
    }

    public static OpponentTrack empty() {
        return EMPTY;
    }

    /** 경과 시간이 늘수록 누적 거리가 줄지 않는가(CelebrityRecord 구간 검사, SPLITS_INVALID). */
    static boolean isOrdered(List<TrackSample> samples) {
        for (int index = 1; index < samples.size(); index++) {
            TrackSample before = samples.get(index - 1);
            TrackSample after = samples.get(index);
            if (after.elapsed().compareTo(before.elapsed()) <= 0
                    || after.distance().compareTo(before.distance()) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 균등 페이스 표(1초 간격). 표본 t = 1 .. ceil(finish초) − 1 에 round(d × t ÷ finish), 마지막 표본은 (finish, d).
     */
    public static OpponentTrack uniform(Distance d, Duration finish) {
        if (finish.isZero() || finish.isNegative()) {
            throw new IllegalArgumentException("완주 시간은 0보다 커야 한다: " + finish);
        }
        double finishSeconds = finish.toMillis() / MILLIS_PER_SECOND;
        long lastWholeSecond = (long) Math.ceil(finishSeconds) - 1;
        List<TrackSample> samples = new ArrayList<>();
        for (long second = 1; second <= lastWholeSecond; second++) {
            long meters = Math.round(d.meters() * second / finishSeconds);
            samples.add(new TrackSample(Duration.ofSeconds(second), Distance.ofMeters(meters)));
        }
        samples.add(new TrackSample(finish, d));
        return new OpponentTrack(samples);
    }

    /** 직선 보간(A-08). 표본이 없으면 0, 마지막 표본 뒤는 마지막 거리. */
    public Distance distanceAt(Duration elapsed) {
        if (samples.isEmpty() || elapsed.isNegative() || elapsed.isZero()) {
            return Distance.ZERO;
        }
        long at = elapsed.toMillis();
        TrackSample first = samples.get(0);
        if (at <= first.elapsed().toMillis()) {
            return beforeFirst(first, at);
        }
        TrackSample last = samples.get(samples.size() - 1);
        if (at >= last.elapsed().toMillis()) {
            return last.distance();
        }
        int index = floorIndex(at);
        return Distance.ofMeters(
                Math.round(between(samples.get(index), samples.get(index + 1), at)));
    }

    /** d에 처음 닿은 경과 시간. 표가 d에 닿지 않으면 비어 있다. */
    public Optional<Duration> arrivalTime(Distance d) {
        if (d.meters() == 0) {
            return Optional.of(Duration.ZERO);
        }
        for (int index = 0; index < samples.size(); index++) {
            TrackSample sample = samples.get(index);
            if (sample.distance().isAtLeast(d)) {
                TrackSample before = index == 0 ? origin() : samples.get(index - 1);
                return Optional.of(Duration.ofMillis(Math.round(reachMillis(before, sample, d))));
            }
        }
        return Optional.empty();
    }

    /** d 이후 표본을 지우고 (도달 시간, d) 표본을 붙인다. d에 닿지 않으면 그대로. */
    public OpponentTrack cut(Distance d) {
        Optional<Duration> reach = arrivalTime(d);
        if (reach.isEmpty()) {
            return this;
        }
        List<TrackSample> kept = new ArrayList<>();
        for (TrackSample sample : samples) {
            if (sample.distance().compareTo(d) < 0 && sample.elapsed().compareTo(reach.get()) < 0) {
                kept.add(sample);
            }
        }
        kept.add(new TrackSample(reach.get(), d));
        return new OpponentTrack(kept);
    }

    public List<TrackSample> samples() {
        return samples;
    }

    /**
     * 앱 전송 형식(BattleStart.opponentTrack, 기록 대결 시작 정보): 시작점 (0, 0) + 경과 시간을 초로 반올림한 표본, 초 오름차순.
     * 반올림으로 같은 초가 되면 뒤 표본(더 먼 거리)을 남긴다.
     */
    public List<TrackSample> wholeSeconds() {
        List<TrackSample> points = new ArrayList<>();
        points.add(origin());
        for (TrackSample sample : samples) {
            long seconds = Math.round(sample.elapsed().toMillis() / MILLIS_PER_SECOND);
            TrackSample point = new TrackSample(Duration.ofSeconds(seconds), sample.distance());
            int last = points.size() - 1;
            if (points.get(last).elapsed().equals(point.elapsed())) {
                points.set(last, point);
            } else {
                points.add(point);
            }
        }
        return points;
    }

    public boolean isEmpty() {
        return samples.isEmpty();
    }

    /** 가장 빠른 1km 구간 시간(유명인 구간 기록이 있을 때 비교표, FR-093). 1km에 닿지 않으면 비어 있다. */
    Optional<Duration> fastestKm() {
        Duration fastest = null;
        for (long km = 1; ; km++) {
            Optional<Duration> end = arrivalTime(Distance.ofMeters(km * Distance.METERS_PER_KM));
            if (end.isEmpty()) {
                return Optional.ofNullable(fastest);
            }
            Duration start =
                    arrivalTime(Distance.ofMeters((km - 1) * Distance.METERS_PER_KM)).orElseThrow();
            Duration split = end.get().minus(start);
            if (fastest == null || split.compareTo(fastest) < 0) {
                fastest = split;
            }
        }
    }

    /** 저장 형식 "경과밀리초:미터;…"(Battle·CelebrityRecord의 문자열 열). */
    String encode() {
        StringBuilder text = new StringBuilder();
        for (TrackSample sample : samples) {
            if (!text.isEmpty()) {
                text.append(SAMPLE_SEPARATOR);
            }
            text.append(sample.elapsed().toMillis())
                    .append(FIELD_SEPARATOR)
                    .append(sample.distance().meters());
        }
        return text.toString();
    }

    static OpponentTrack decode(String text) {
        if (text == null || text.isBlank()) {
            return EMPTY;
        }
        List<TrackSample> parsed = new ArrayList<>();
        for (String pair : text.split(SAMPLE_SEPARATOR)) {
            String[] fields = pair.split(FIELD_SEPARATOR);
            parsed.add(
                    new TrackSample(
                            Duration.ofMillis(Long.parseLong(fields[0])),
                            Distance.ofMeters(Long.parseLong(fields[1]))));
        }
        return of(parsed);
    }

    private static Distance beforeFirst(TrackSample first, long at) {
        long firstMillis = first.elapsed().toMillis();
        if (firstMillis == 0) {
            return first.distance();
        }
        return Distance.ofMeters(Math.round(first.distance().meters() * (double) at / firstMillis));
    }

    private int floorIndex(long at) {
        int low = 0;
        int high = samples.size() - 1;
        while (low < high - 1) {
            int middle = (low + high) >>> 1;
            if (samples.get(middle).elapsed().toMillis() <= at) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static double between(TrackSample from, TrackSample to, long at) {
        double fromMillis = from.elapsed().toMillis();
        double ratio = (at - fromMillis) / (to.elapsed().toMillis() - fromMillis);
        return from.distance().meters()
                + ratio * (to.distance().meters() - from.distance().meters());
    }

    private static double reachMillis(TrackSample before, TrackSample sample, Distance d) {
        long gained = sample.distance().meters() - before.distance().meters();
        if (gained == 0) {
            return sample.elapsed().toMillis();
        }
        double ratio = (double) (d.meters() - before.distance().meters()) / gained;
        long span = sample.elapsed().toMillis() - before.elapsed().toMillis();
        return before.elapsed().toMillis() + ratio * span;
    }

    private static TrackSample origin() {
        return new TrackSample(Duration.ZERO, Distance.ZERO);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof OpponentTrack other && samples.equals(other.samples);
    }

    @Override
    public int hashCode() {
        return samples.hashCode();
    }

    @Override
    public String toString() {
        return "OpponentTrack" + samples.size();
    }
}
