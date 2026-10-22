package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/**
 * 크루 한 곳(서버 crew.CrewDetailView). [myRole]은 크루 밖이면 null이고, [inviteCode]·[inviteCodeExpiresAt]은 크루장에게만 온다.
 */
data class CrewDetail(
    val crewId: Long,
    val name: String,
    val intro: String?,
    val tags: Set<String>,
    val visibility: CrewVisibility,
    val joinPolicy: JoinPolicy,
    val logoUrl: String?,
    val leaderId: Long,
    val leaderName: String,
    val headcount: Int,
    val weeklyDistance: Distance,
    val myRole: CrewRole?,
    val inviteCode: String?,
    val inviteCodeExpiresAt: Instant?
) {
    val isLeader: Boolean get() = myRole == CrewRole.LEADER
}
