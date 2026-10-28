package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/**
 * 주간 레이스(서버 crew.RaceStatusView, FR-140)와 공동 목표(FR-138~FR-141). [goal]이 없으면 이번 주 목표가 없고,
 * [achievedAt]·[celebration]은 이번 주 목표를 달성했을 때만 있다.
 */
data class RaceStatus(
    val total: Distance,
    val closesAt: Instant,
    val contributors: List<Contributor>,
    val myShare: Double?,
    val goal: Distance? = null,
    val goalProgressPercent: Int? = null,
    val achievedAt: Instant? = null,
    val celebration: CelebrationCard? = null
) {
    /** [memberId]는 탈퇴했으면 null, [isMe]는 열람자 본인이다. */
    data class Contributor(
        val displayName: String,
        val distance: Distance,
        val share: Double,
        val memberId: Long? = null,
        val isMe: Boolean = false
    )
}
