package com.a207.runkk.running;

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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 러닝 한 회의 심박 측정값(설계 6.1.3). 원자료를 파기해도 요약(평균·최고)은 남는다(FR-029). */
public class HeartRateSeries {

    private static final Duration LAST_SAMPLE = Duration.ofSeconds(1);

    private final LocalDateTime origin;
    private List<HeartRateSample> samples;
    private boolean purged;
    private HeartRateSummary summary;

    HeartRateSeries(
            LocalDateTime origin,
            List<HeartRateSample> samples,
            boolean purged,
            HeartRateSummary summary) {
        this.origin = origin;
        this.samples = new ArrayList<>(samples);
        this.purged = purged;
        this.summary = summary;
    }

    static HeartRateSeries of(LocalDateTime origin, List<HeartRateSample> samples) {
        HeartRateSeries series = new HeartRateSeries(origin, List.of(), false, null);
        series.append(samples);
        return series;
    }

    /**
     * 덧붙이고 요약을 다시 계산한다. 같은 측정 시각은 한 번만 남긴다(먼저 받은 값 유지). 파기한 뒤에는 덧붙이지 않는다(IllegalStateException).
     */
    public void append(List<HeartRateSample> s) {
        if (purged) {
            throw new IllegalStateException("심박 원자료를 파기한 기록");
        }
        // 측정 시각이 같은 표본은 먼저 받은 값을 남긴다(앱의 재전송이 중복을 만들지 않게, FR-043).
        Map<LocalDateTime, HeartRateSample> byInstant = new LinkedHashMap<>();
        samples.forEach(x -> byInstant.putIfAbsent(x.measuredAt(), x));
        s.forEach(x -> byInstant.putIfAbsent(x.measuredAt(), x));
        List<HeartRateSample> merged = new ArrayList<>(byInstant.values());
        merged.sort(Comparator.comparing(HeartRateSample::measuredAt));
        this.samples = merged;
        if (!merged.isEmpty()) {
            int max = merged.stream().mapToInt(HeartRateSample::bpm).max().orElseThrow();
            int avg =
                    (int)
                            Math.round(
                                    merged.stream()
                                            .mapToInt(HeartRateSample::bpm)
                                            .average()
                                            .orElseThrow());
            this.summary = new HeartRateSummary(avg, max);
        }
    }

    public HeartRateSummary summary() {
        return summary;
    }

    /** 5구간 시간. 표본 하나의 시간은 다음 표본까지(마지막은 1초). 50% 미만은 세지 않는다(FR-185). */
    public Map<HeartRateZone, Duration> zoneDistribution(int maxHr) {
        Map<HeartRateZone, Duration> result = new EnumMap<>(HeartRateZone.class);
        if (maxHr <= 0) {
            return result;
        }
        for (int i = 0; i < samples.size(); i++) {
            HeartRateSample s = samples.get(i);
            Duration span =
                    i + 1 < samples.size()
                            ? Duration.between(s.measuredAt(), samples.get(i + 1).measuredAt())
                            : LAST_SAMPLE;
            HeartRateZone.of(s.bpm(), maxHr).ifPresent(z -> result.merge(z, span, Duration::plus));
        }
        return result;
    }

    /** 원자료 삭제, 요약 유지. */
    public void purge() {
        this.samples = new ArrayList<>();
        this.purged = true;
    }

    public boolean isPurged() {
        return purged;
    }

    public List<HeartRateSample> samples() {
        return Collections.unmodifiableList(samples);
    }

    byte[] encodeSamples() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(samples.size());
            for (HeartRateSample s : samples) {
                out.writeLong(Duration.between(origin, s.measuredAt()).toMillis());
                out.writeShort(s.bpm());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("심박 직렬화 실패", e);
        }
        return bytes.toByteArray();
    }

    static List<HeartRateSample> decodeSamples(LocalDateTime origin, byte[] data) {
        if (data == null || data.length == 0) {
            return List.of();
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int size = in.readInt();
            List<HeartRateSample> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                LocalDateTime at = origin.plusNanos(in.readLong() * 1_000_000L);
                result.add(new HeartRateSample(at, in.readShort()));
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("심박 해제 실패", e);
        }
    }
}
