package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.OpponentProgress
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** 내 측정값(RunTracker.progress에서 옮긴 값). [locked]는 화면 잠금(FR-182) 상태다. */
data class MyProgress(
    val distance: Distance,
    val elapsed: Duration,
    val currentPace: Pace?,
    val averagePace: Pace?,
    val locked: Boolean
) {
    companion object {
        val ZERO = MyProgress(Distance.ZERO, Duration.ZERO, currentPace = null, averagePace = null, locked = false)
    }
}
