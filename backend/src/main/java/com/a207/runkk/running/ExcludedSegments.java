package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 유효 판정에서 뺀 구간(FR-047 v0.10): 이웃 포인트 사이 시속이 ValidityRule.maxSpeedKmh를 넘는 쌍(순간이동·차량 이동). 불안정 구간 끝 쌍과
 * 정지 경계 쌍은 처음부터 거리에 들지 않으므로 여기에 넣지 않는다. 쌍마다 [앞 포인트 시각, 뒤 포인트 시각]을 시간 구간으로 두어 RunTrack 계산이 정지 구간과 같은
 * 방식(PauseInterval.straddles)으로 그 쌍의 거리를 빼고 그 시간을 운동 시간에서 뺀다.
 *
 * @param intervals 뺀 쌍의 시간 구간(시간순)
 * @param distance 뺀 거리의 합
 * @param elapsed 뺀 시간의 합
 */
public record ExcludedSegments(List<PauseInterval> intervals, Distance distance, Duration elapsed) {

    public static final ExcludedSegments NONE =
            new ExcludedSegments(List.of(), Distance.ZERO, Duration.ZERO);

    public ExcludedSegments {
        intervals = List.copyOf(intervals);
    }

    public boolean isEmpty() {
        return intervals.isEmpty();
    }

    /** 정지 구간과 뺀 구간을 시간순으로 합친다. RunTrack의 거리·도달 시간·곡선 계산이 둘을 같이 건너뛴다. */
    public List<PauseInterval> withPauses(List<PauseInterval> pauses) {
        if (intervals.isEmpty()) {
            return pauses;
        }
        List<PauseInterval> all = new ArrayList<>(pauses);
        all.addAll(intervals);
        all.sort(Comparator.comparing(PauseInterval::from));
        return List.copyOf(all);
    }
}
