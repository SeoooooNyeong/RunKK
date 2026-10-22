package com.a207.runkk.running;

import com.a207.runkk.common.GeoPoint;
import java.time.LocalDateTime;

/**
 * 측정된 위치 하나(자료사전 유효위치포인트 = 위치포인트 + 신호상태). 정확도 30m 이하만 기기에서 넘어온다(FR-032).
 *
 * @param measuredAt 측정 일시
 * @param point 좌표
 * @param accuracyMeters 정확도 반경(m)
 * @param signal 신호 상태. UNSTABLE이면 신호 공백 뒤 첫 포인트다(FR-033)
 */
public record LocationPoint(
        LocalDateTime measuredAt, GeoPoint point, double accuracyMeters, SignalState signal) {

    public LocationPoint {
        if (measuredAt == null || point == null || signal == null) {
            throw new IllegalArgumentException("위치 포인트 항목이 비었다");
        }
        if (accuracyMeters < 0) {
            throw new IllegalArgumentException("정확도는 0 이상: " + accuracyMeters);
        }
    }
}
