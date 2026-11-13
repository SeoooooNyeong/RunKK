package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RouteLine
import java.time.Duration

/**
 * 크루런 화면에 보일 내 측정값(RunTracker 진행값에서 옮긴 값). ui.crew는 domain.run을 보지 않으므로 여기로 옮긴다(5.1.2).
 * [route]는 측정 중 경로선(RunTracker.routeLine, D-261)이다.
 */
data class CrewRunProgress(
    val distance: Distance,
    val elapsed: Duration,
    val currentPace: Pace?,
    val averagePace: Pace?,
    val isPaused: Boolean = false,
    val route: RouteLine = RouteLine.EMPTY
) {
    companion object {
        val ZERO = CrewRunProgress(Distance.ZERO, Duration.ZERO, currentPace = null, averagePace = null)
    }
}
