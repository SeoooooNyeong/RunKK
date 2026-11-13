package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

@Serializable
internal data class CrewMembersResponse(val members: List<Member> = emptyList()) {
    @Serializable
    data class Member(
        val memberId: Long,
        val displayName: String? = null,
        val role: String,
        val crewIntro: String? = null,
        val tier: String? = null,
        val runimalType: String? = null,
        val me: Boolean = false,
        /** 이 크루에 반영된 누적 거리(m, contract-W7 2장). */
        val accumulatedMeters: Long = 0,
        val joinedAt: String? = null,
        val level: Int? = null,
        val ageBand: String? = null,
        val gender: String? = null,
        /** 본인 줄만(PUBLIC·PRIVATE). */
        val ageBandVisibility: String? = null,
        val genderVisibility: String? = null
    )
}
