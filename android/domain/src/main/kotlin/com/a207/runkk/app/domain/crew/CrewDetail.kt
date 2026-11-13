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
    /** 개설 일시(피그마 F-02·F-18 "yyyy. mm. dd", contract-W4 1절). 옛 응답이면 null. */
    val createdAt: Instant? = null,
    /** 누적 크루 거리: 개설 뒤 모든 주의 크루 주간 거리 합(W7 F-18 "누적 거리"). */
    val totalDistance: Distance = Distance(0),
    /** 누적 크루런 횟수: 마친 크루런 수(F-02 "누적 크루런 횟수"). */
    val crewRunCount: Int = 0,
    /** 최근 마친 크루런 최대 3건, 약속 시각 최신순(크루 밖도 본다, 결정 C9). */
    val recentCrewRuns: List<RecentCrewRun> = emptyList(),
    /** 열람자가 크루를 나온 뒤 24시간 가입 제한 중이면 가입할 수 있는 시각, 아니면 null(서버 rejoinAvailableAt, F-02c). */
    val joinableAt: Instant? = null
) {
    val isLeader: Boolean get() = myRole == CrewRole.LEADER
}
