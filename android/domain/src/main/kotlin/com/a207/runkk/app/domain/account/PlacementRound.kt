package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import java.time.Duration

/**
 * 센 판 하나. [battleDistance]는 고른 대결 거리, [distance]·[elapsed]·[averagePace]는 그 판 러닝 기록 값,
 * [verdict]는 서버 PlacementVerdict 이름(WIN·LOSE·DRAW, 정정되면 바뀐다).
 */
data class PlacementRound(
    val round: Int,
    val battleId: Long,
    val battleDistance: Distance,
    val distance: Distance,
    val elapsed: Duration,
    val averagePace: Pace?,
    val verdict: String
)
