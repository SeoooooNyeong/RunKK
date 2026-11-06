package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.live.BattleGuidance
import kotlin.math.abs

/**
 * 기록 대결(고스트·나와의 싸움·유명인)의 격차·추월·도착 판정(6.1.14). 랜덤 대결은 서버가 같은 규칙으로
 * BattleGuidance를 보낸다(A-09). 흔들림을 막으려고 10m를 넘는 차로 선두가 바뀔 때만 추월로 본다(FR-188).
 */
class GapTracker(private val battleDistance: Distance) {
    private enum class Leader { ME, OPPONENT }

    private var leader: Leader? = null

    /** 나 − 상대(m). 앞서면 양수. */
    var gapMeters: Int = 0
        private set

    /** 격차를 갱신하고, 선두가 바뀌었으면 OVERTOOK(내가 앞섬)·OVERTAKEN(상대가 앞섬)을 돌려준다. */
    fun update(mine: Distance, theirs: Distance): BattleGuidance.Kind? {
        val gap = (mine.meters - theirs.meters).toInt()
        gapMeters = gap
        if (abs(gap) <= OVERTAKE_MARGIN_METERS) return null
        val newLeader = if (gap > 0) Leader.ME else Leader.OPPONENT
        val previous = leader
        leader = newLeader
        if (previous == null || previous == newLeader) return null
        return if (newLeader == Leader.ME) BattleGuidance.Kind.OVERTOOK else BattleGuidance.Kind.OVERTAKEN
    }

    /** 반올림 전 누적 거리(m)가 대결 거리에 닿았는가. 999.7m는 표시로는 1,000m여도 닿지 않았다(W54). */
    fun hasArrived(mineMeters: Double): Boolean = mineMeters >= battleDistance.meters

    private companion object {
        const val OVERTAKE_MARGIN_METERS = 10
    }
}
