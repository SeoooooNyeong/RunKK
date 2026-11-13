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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 움직임 표본(속도·케이던스)의 저장 형식(run_record.motion_data, V41). 경로처럼 압축 바이너리다: 개수, 표본마다 시작 기준 밀리초, 속도,
 * 케이던스(double, 없으면 NaN).
 */
final class MotionSamples {

    private MotionSamples() {}

    /**
     * 측정 일시 순으로 정렬하고 같은 일시의 표본은 하나로 합친다. 값마다 먼저 받은 값을 남긴다(속도만 잰 표본과 케이던스만 잰 표본이 같은 일시면 둘 다 남는다).
     */
    static List<MotionSample> normalize(List<MotionSample> samples) {
        Map<LocalDateTime, MotionSample> byInstant = new LinkedHashMap<>();
        samples.forEach(s -> byInstant.merge(s.measuredAt(), s, MotionSamples::merge));
        List<MotionSample> sorted = new ArrayList<>(byInstant.values());
        sorted.sort(Comparator.comparing(MotionSample::measuredAt));
        return sorted;
    }

    private static MotionSample merge(MotionSample first, MotionSample later) {
        return new MotionSample(
                first.measuredAt(),
                first.speedMps() != null ? first.speedMps() : later.speedMps(),
                first.cadenceSpm() != null ? first.cadenceSpm() : later.cadenceSpm());
    }

    /**
     * max개를 넘으면 같은 일시를 먼저 합치고(normalize), 그래도 넘치면 시간 칸으로 묶어 칸마다 속도·케이던스를 따로 평균한 사본(칸의 일시는 칸 첫 표본).
     * 칸 길이는 (첫~끝 길이 ÷ max)에서 시작해 10%씩 늘린다. 상한을 넘었다고 기록을 거부하지 않고 해상도만 낮추며, 한 종류 값만 든 표본이 번갈아 와도 두 값이
     * 모두 남는다(MR !91 AN-5, 확인 리뷰 CR2-B1). max개 이하면 그대로의 사본.
     */
    static List<MotionSample> capped(List<MotionSample> samples, int max) {
        if (samples.size() <= max) {
            return List.copyOf(samples);
        }
        List<MotionSample> merged = normalize(samples);
        if (merged.size() <= max) {
            return List.copyOf(merged);
        }
        LocalDateTime origin = merged.get(0).measuredAt();
        long span = Duration.between(origin, merged.get(merged.size() - 1).measuredAt()).toMillis();
        long bucket = Math.max(1, (span + max) / max);
        List<MotionSample> binned = bin(merged, origin, bucket);
        while (binned.size() > max) {
            bucket += bucket / 10 + 1;
            binned = bin(merged, origin, bucket);
        }
        return List.copyOf(binned);
    }

    /** 시간순 표본을 origin부터 bucketMillis 칸으로 묶어 칸마다 속도·케이던스를 따로 평균한다. */
    private static List<MotionSample> bin(
            List<MotionSample> sorted, LocalDateTime origin, long bucketMillis) {
        List<MotionSample> out = new ArrayList<>();
        int i = 0;
        while (i < sorted.size()) {
            long index =
                    Duration.between(origin, sorted.get(i).measuredAt()).toMillis() / bucketMillis;
            LocalDateTime at = sorted.get(i).measuredAt();
            double speedSum = 0;
            double cadenceSum = 0;
            int speeds = 0;
            int cadences = 0;
            while (i < sorted.size()
                    && Duration.between(origin, sorted.get(i).measuredAt()).toMillis()
                                    / bucketMillis
                            == index) {
                MotionSample s = sorted.get(i);
                if (s.speedMps() != null) {
                    speedSum += s.speedMps();
                    speeds++;
                }
                if (s.cadenceSpm() != null) {
                    cadenceSum += s.cadenceSpm();
                    cadences++;
                }
                i++;
            }
            out.add(
                    new MotionSample(
                            at,
                            speeds == 0 ? null : speedSum / speeds,
                            cadences == 0 ? null : cadenceSum / cadences));
        }
        return out;
    }

    /** 비어 있으면 null(열을 비운다). */
    static byte[] encode(LocalDateTime origin, List<MotionSample> samples) {
        if (samples.isEmpty()) {
            return null;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeInt(samples.size());
            for (MotionSample s : samples) {
                out.writeLong(Duration.between(origin, s.measuredAt()).toMillis());
                out.writeDouble(s.speedMps() == null ? Double.NaN : s.speedMps());
                out.writeDouble(s.cadenceSpm() == null ? Double.NaN : s.cadenceSpm());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("움직임 표본 압축 실패", e);
        }
        return bytes.toByteArray();
    }

    static List<MotionSample> decode(LocalDateTime origin, byte[] data) {
        if (data == null || data.length == 0) {
            return List.of();
        }
        try (DataInputStream in =
                new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(data)))) {
            int size = in.readInt();
            List<MotionSample> result = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                LocalDateTime at = origin.plusNanos(in.readLong() * 1_000_000L);
                double speed = in.readDouble();
                double cadence = in.readDouble();
                result.add(
                        new MotionSample(
                                at,
                                Double.isNaN(speed) ? null : speed,
                                Double.isNaN(cadence) ? null : cadence));
            }
            return List.copyOf(result);
        } catch (IOException e) {
            throw new UncheckedIOException("움직임 표본 해제 실패", e);
        }
    }
}
