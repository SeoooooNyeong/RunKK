package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/** 주간 레이스(서버 crew.RaceStatusView, FR-140). */
data class RaceStatus(
    val total: Distance,
    val closesAt: Instant,
    val contributors: List<Contributor>,
    val myShare: Double?
) {
    /** [memberId]는 탈퇴했으면 null, [me]는 열람자 본인이다. */
    data class Contributor(
        val displayName: String,
        val distance: Distance,
        val share: Double,
        val memberId: Long? = null,
        val me: Boolean = false
    )
}
