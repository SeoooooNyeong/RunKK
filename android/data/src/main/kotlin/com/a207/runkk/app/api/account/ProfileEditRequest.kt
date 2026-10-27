package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

/** PATCH /me/profile 요청(서버 MeController.ProfileEditRequest). tags는 v0.11에서 지웠다(D-91). */
@Serializable
internal data class ProfileEditRequest(val nickname: String, val intro: String?)
