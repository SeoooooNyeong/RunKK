package com.a207.runkk.analysis;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;

/**
 * 하루의 실제 러닝(설계 6.1.4 RunningGoal.checkOn의 입력).
 *
 * @param distance 유효 거리 합
 * @param pace 평균 페이스, 기록이 없으면 null
 */
public record DayActual(Distance distance, Pace pace) {

    /** 기록 없는 날. */
    public static final DayActual NONE = new DayActual(Distance.ZERO, null);
}
