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
