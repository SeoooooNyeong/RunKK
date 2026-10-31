package com.a207.runkk.app.api.account

import kotlinx.serialization.Serializable

/**
 * PATCH /me/profile 요청(서버 MeController.ProfileEditRequest). tags는 v0.11에서 지웠다(D-91).
 * v0.14(D-287, 서버 D-241·D-242): 나이대·성별 공개(PUBLIC·PRIVATE)를 더하고 사진(photoUrl)은 보내지 않는다.
 */
@Serializable
internal data class ProfileEditRequest(
    val nickname: String,
    val intro: String?,
    val ageBandVisibility: String,
    val genderVisibility: String,
    /** 성별(FEMALE·MALE·UNDISCLOSED). null이면 서버가 그대로 둔다. */
    val gender: String? = null,
    /** 출생연도(4자리). null이면 서버가 그대로 둔다. */
    val birthYear: Int? = null
)
