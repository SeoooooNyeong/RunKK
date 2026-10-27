package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

/** POST /members/guest 201 응답(서버 SignUpController.GuestStartResponse). guestCredential은 다시 내주지 않는다(D-89). */
@Serializable
internal data class GuestStartResponse(
    val memberId: Long,
    val nickname: String? = null,
    val accessToken: String,
    val refreshToken: String,
    val guestCredential: String
) {
    override fun toString(): String = "GuestStartResponse(memberId=$memberId)"
}
