package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

/** POST /members/guest 요청(서버 SignUpController.GuestStartRequest, FR-197). HEART_RATE·FACE_TRANSFER는 받지 않는다. */
@Serializable
internal data class GuestStartRequest(val consents: Map<String, Boolean>)
