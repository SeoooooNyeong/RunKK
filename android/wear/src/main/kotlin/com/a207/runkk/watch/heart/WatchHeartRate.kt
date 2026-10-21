package com.a207.runkk.watch.heart

/**
 * 워치 센서가 잰 심박 한 건(6.1.15 HeartRateCollector.samples).
 * 실패 측정(센서 접촉 없음·신뢰 불가)도 버리지 않고 [Status.FAILED]로 표시해 폰(data.watch)이 거르게 한다(FR-041).
 */
data class WatchHeartRate(val measuredAtEpochMillis: Long, val bpm: Int, val status: Status) {
    enum class Status {
        OK,
        FAILED
    }
}
