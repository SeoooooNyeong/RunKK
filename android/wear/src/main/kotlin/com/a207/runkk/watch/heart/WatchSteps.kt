package com.a207.runkk.watch.heart

/**
 * 동행 운동 세션이 잰 손목 걸음 한 건(S15P31A207-242). STEPS_PER_MINUTE을 지원하면 [perMinute], STEPS만 지원하면 이 세션의 누적 걸음
 * 수 [total]이다. 폰(data.watch)이 케이던스로 바꾼다.
 */
data class WatchSteps(val measuredAtEpochMillis: Long, val perMinute: Long? = null, val total: Long? = null)
