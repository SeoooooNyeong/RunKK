package com.a207.runkk.watch.alert

/**
 * 근접 알림 일정용 단조 시계(부팅 뒤 경과 ms). 구현 [SystemMonotonicClock]은 SystemClock.elapsedRealtime이다.
 * 벽시계(Clock)는 러닝 중 NTP·수동 시각 변경으로 뛰어 만료·간격이 어긋나므로 일정에 쓰지 않는다.
 */
fun interface MonotonicClock {
    fun elapsedMillis(): Long
}
