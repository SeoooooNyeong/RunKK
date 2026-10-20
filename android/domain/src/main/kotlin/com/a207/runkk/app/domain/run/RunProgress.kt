package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.SignalState
import java.time.Duration

/**
 * 1초마다의 진행 지표(FR-034, A-02): 경과, 누적 거리, 현재·평균 페이스, 신호 상태, 잠금 여부.
 * heartRate는 최근 30초 안의 심박(없으면 null)이고 호흡 안내와 워치 표시가 쓴다.
 */
data class RunProgress(
    val elapsed: Duration,
    val distance: Distance,
    val currentPace: Pace?,
    val averagePace: Pace?,
    val signal: SignalState?,
    val locked: Boolean,
    val heartRate: HeartRateSample?
) {
    companion object {
        val INITIAL =
            RunProgress(
                elapsed = Duration.ZERO,
                distance = Distance.ZERO,
                currentPace = null,
                averagePace = null,
                signal = null,
                locked = false,
                heartRate = null
            )
    }
}
