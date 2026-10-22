package com.a207.runkk.running;

import com.a207.runkk.common.StandardDistance;

/**
 * 신기록 하나(설계 6.1.3, FR-063, FR-186).
 *
 * @param kind 최장 거리 또는 기준 거리
 * @param distance 기준 거리(최장이면 null)
 * @param previous 이전 값 표기("직전 5.23 km" 또는 이전 시간 "25:00")
 */
public record NewRecordMark(Kind kind, StandardDistance distance, String previous) {

    public NewRecordMark {
        if (kind == null || previous == null) {
            throw new IllegalArgumentException("신기록 항목이 비었다");
        }
        if ((kind == Kind.BASE_DISTANCE) != (distance != null)) {
            throw new IllegalArgumentException("기준 거리 신기록만 거리를 가진다");
        }
    }

    /** 신기록 종류(설계 6.1.3 "kind(LONGEST / BASE_DISTANCE)"). */
    public enum Kind {
        LONGEST,
        BASE_DISTANCE
    }
}
