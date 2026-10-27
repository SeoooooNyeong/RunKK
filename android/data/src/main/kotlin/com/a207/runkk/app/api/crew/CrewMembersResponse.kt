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
        val me: Boolean = false
    )
}
