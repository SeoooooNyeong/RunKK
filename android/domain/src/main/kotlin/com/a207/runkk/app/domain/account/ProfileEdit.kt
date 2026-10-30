package com.a207.runkk.app.domain.account

import com.a207.runkk.app.core.model.Gender

/**
 * 프로필 편집 값(PATCH /me/profile, D-287, FR-007·FR-012). 나이대·성별 공개는 스위치 하나씩이다(켬 = PUBLIC).
 * 사진은 보내지 않는다.
 */
data class ProfileEdit(
    val nickname: String,
    val intro: String?,
    val isAgePublic: Boolean,
    val isGenderPublic: Boolean,
    /** 바꿀 성별. null이면 그대로 둔다(서버 PATCH null 항목 유지). */
    val gender: Gender? = null,
    /** 바꿀 출생연도(4자리). null이면 그대로 둔다. 만 14세 이하면 서버가 UNDER_14로 거부한다. */
    val birthYear: Int? = null
)
