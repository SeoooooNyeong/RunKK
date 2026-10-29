package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RouteLine
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.OpponentProgress
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 내 측정값(RunTracker.progress에서 옮긴 값). [route]는 측정 중 경로선(RunTracker.routeLine, D-261)이고 ui.battle은 domain.run을
 * 보지 않으므로 여기로 옮긴다. 앱 화면 잠금은 없다(D-256).
 */
data class MyProgress(
    val distance: Distance,
    val elapsed: Duration,
    val currentPace: Pace?,
    val averagePace: Pace?,
    val route: RouteLine = RouteLine.EMPTY
) {
    companion object {
        val ZERO = MyProgress(Distance.ZERO, Duration.ZERO, currentPace = null, averagePace = null)
    }
}
