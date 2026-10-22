package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import java.util.List;

/**
 * 1초 간격 (경과 시간, 누적 거리) 표본을 cut 거리까지(설계 6.1.3). battle이 OpponentTrack으로 바꿔 쓴다. 좌표 필드가 없어 위치가 새지 않는다
 * (NFR-021).
 *
 * @param samples 표본
 * @param cut 자른 거리
 */
public record RunCurve(List<CurvePoint> samples, Distance cut) {

    public RunCurve {
        samples = List.copyOf(samples);
    }
}
