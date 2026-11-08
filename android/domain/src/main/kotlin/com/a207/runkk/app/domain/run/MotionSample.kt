package com.a207.runkk.app.domain.run

import java.time.Instant

/**
 * 기기가 잰 움직임 표본(측정일시, 속도 m/s, 케이던스 분당 걸음 수). 둘 중 하나만 있어도 된다(S15P31A207-242, 2026-11-08 결정
 * "다 보내, 없으면 계산하고"). 폰은 위치 속도·걸음 센서, 워치는 Health Services SPEED·STEPS_PER_MINUTE로 잰다. 재지 못한 값은 null이고
 * 서버가 속도는 위치로 셈하고 케이던스는 비운다.
 */
data class MotionSample(val at: Instant, val speedMps: Double? = null, val cadenceSpm: Double? = null) {
    /** 값이 하나라도 있고 모두 0 이상의 유한한 값이다. 아니면 버린다. */
    val isUsable: Boolean
        get() = (speedMps != null || cadenceSpm != null) && speedMps.isMeasure() && cadenceSpm.isMeasure()

    private fun Double?.isMeasure(): Boolean = this == null || (isFinite() && this >= 0)
}
