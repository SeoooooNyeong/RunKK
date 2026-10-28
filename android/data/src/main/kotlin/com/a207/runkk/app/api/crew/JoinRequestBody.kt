package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** POST /crews/join: 크루 ID로 신청하거나 초대 코드로 신청한다(MembershipService.apply). */
@Serializable
internal data class JoinRequestBody(val crewId: Long? = null, val inviteCode: String? = null)
