package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/**
 * 크루원 목록(서버 crew.CrewMembersView + Runimal 유형, D-46·D-52). 비공개 크루원은 표시명·역할·소개만 온다.
 * [Member.me]는 열람자 본인 줄이다. role은 서버 역할 이름을 CrewRole로 바꾼 값이다(모르는 이름은 MEMBER).
 */
data class CrewMembers(val members: List<Member>) {
    data class Member(
        val memberId: Long,
        val displayName: String,
        val role: CrewRole,
        val crewIntro: String?,
        val tier: String?,
        val runimalType: String?,
        val me: Boolean = false
    )
}
