package com.a207.runkk.app.domain.run

import java.time.Duration
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 격차·설정·연결로 워치 근접 알림을 만든다(규칙 한 곳, FR-196, D-96, A-23). 두 연산 모두 상태가 없고(D-119) 마지막으로 보낸 값·시각은
 * RunWatchBridge가 갖는다. 상수는 SRS (제안) 값이라 이 클래스에 둔다(D-113).
 */
object ProximityAlertRule {
    /** 켜는 거리: |격차| ≤ 100m. */
    const val ACTIVE_WITHIN_METERS = 100

    /** 간격 기준 거리 아래 끝(이보다 가까우면 최소 간격). */
    const val NEAREST_METERS = 5

    val MIN_INTERVAL: Duration = Duration.ofMillis(300)
    val MAX_INTERVAL: Duration = Duration.ofMillis(2_000)

    /** 다시 보내는 간격 변화. */
    val RESEND_INTERVAL_CHANGE: Duration = Duration.ofMillis(100)

    /** 유지 신호 주기(워치는 3초 동안 새 메시지가 없으면 멈춘다). */
    val KEEP_ALIVE: Duration = Duration.ofSeconds(2)

    private const val ROUNDING_MILLIS = 10.0

    /**
     * 근접 알림 켜짐 ∧ 워치 연결 ∧ 격차 있음(대결에서만 값이 있다, D-137) ∧ |격차| ≤ 100m이면 알림, 아니면 null.
     * 간격 = 300 + 1700 × (clamp(|격차|, 5, 100) − 5) ÷ 95 ms를 10ms 단위로 반올림한다(D-158 (1)). 앞섬은 격차 > 0(0은 거짓, D-124).
     */
    fun evaluate(gapMeters: Int?, isAlertOn: Boolean, isWatchConnected: Boolean): ProximityAlert? {
        if (!isAlertOn) return null
        if (!isWatchConnected) return null
        if (gapMeters == null) return null
        val gap = abs(gapMeters)
        if (gap > ACTIVE_WITHIN_METERS) return null
        val clamped = gap.coerceIn(NEAREST_METERS, ACTIVE_WITHIN_METERS)
        val span = (MAX_INTERVAL.toMillis() - MIN_INTERVAL.toMillis()).toDouble()
        val raw = MIN_INTERVAL.toMillis() + span * (clamped - NEAREST_METERS) / (ACTIVE_WITHIN_METERS - NEAREST_METERS)
        val millis = (raw / ROUNDING_MILLIS).roundToLong() * ROUNDING_MILLIS.toLong()
        return ProximityAlert(gap, Duration.ofMillis(millis), isAhead = gapMeters > 0)
    }

    /**
     * 보낼 때인가(F.4.3, D-158 (2)): 이 회에 보낸 적이 없으면([sinceLastSent] null) 다음 알림이 있을 때만, 켜짐·꺼짐이 바뀌면,
     * 간격이 100ms 이상 바뀌면, 그 밖에는 마지막 전송 뒤 2초가 지났을 때(유지 신호).
     */
    fun shouldSend(previous: ProximityAlert?, next: ProximityAlert?, sinceLastSent: Duration?): Boolean {
        if (sinceLastSent == null) return next != null
        if ((previous == null) != (next == null)) return true
        if (previous == null || next == null) return false
        if (next.interval.minus(previous.interval).abs() >= RESEND_INTERVAL_CHANGE) return true
        return sinceLastSent >= KEEP_ALIVE
    }
}
