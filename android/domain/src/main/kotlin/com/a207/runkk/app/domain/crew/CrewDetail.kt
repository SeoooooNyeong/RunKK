package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/**
 * 크루 한 곳(서버 crew.CrewDetailView). [myRole]은 크루 밖이면 null이고, [inviteCode]·[inviteCodeExpiresAt]은 크루원 모두에게 온다
 * (피그마 F-18b). 재발급은 크루장만 한다.
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
    val inviteCodeExpiresAt: Instant?,
    /** 지난 주 크루 거리(크루 상세 통계, 피그마 F-02). */
    val lastWeekDistance: Distance = Distance(0),
    /** 개설일(F-18 "yyyy. mm. dd", contract-W4 createdAt). 옛 서버면 null. */
    val createdAt: LocalDate? = null,
    /** 누적 크루 거리(F-18 "누적 거리", contract-W4 totalDistanceMeters). 옛 서버면 null. */
    val totalDistance: Distance? = null,
    /** 누적 크루런 횟수(F-18, contract-W4 crewRunCount). 옛 서버면 null. */
    val crewRunCount: Int? = null
) {
    val isLeader: Boolean get() = myRole == CrewRole.LEADER
}
