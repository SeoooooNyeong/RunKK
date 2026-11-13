package com.a207.runkk.app.ui.crew

import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 24시간 가입 제한 카운트다운(피그마 F-02c, contract-W4 1·3절 rejoinAvailableAt). [start]가 1초마다 남은 시간을 [onTick]으로 알리고,
 * 시각이 지나면 null을 한 번 알린 뒤 멈춘다. 크루 찾기와 크루 검색이 함께 쓴다.
 */
internal class JoinCooldown(private val clock: Clock) {
    private var job: Job? = null

    fun start(scope: CoroutineScope, until: Instant?, onTick: (Duration?) -> Unit) {
        job?.cancel()
        if (until == null || !until.isAfter(clock.instant())) {
            onTick(null)
            return
        }
        job = scope.launch {
            while (true) {
                val left = Duration.between(clock.instant(), until)
                if (left.isNegative || left.isZero) {
                    onTick(null)
                    break
                }
                // 남은 초는 올림으로 보인다(00:00:00이 1초 동안 보이지 않게).
                onTick(left.plusMillis(MILLIS_TO_CEIL).withNanos(0))
                delay(TICK_MILLIS)
            }
        }
    }

    private companion object {
        const val TICK_MILLIS = 1_000L
        const val MILLIS_TO_CEIL = 999L
    }
}
