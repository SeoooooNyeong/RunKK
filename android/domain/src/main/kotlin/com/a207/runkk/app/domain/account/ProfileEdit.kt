package com.a207.runkk.app.domain.account

/**
 * 프로필 편집 값(PATCH /me/profile, D-287, FR-007·FR-012). 나이대·성별 공개는 스위치 하나씩이다(켬 = PUBLIC).
 * 사진은 보내지 않는다.
 */
data class ProfileEdit(val nickname: String, val intro: String?, val isAgePublic: Boolean, val isGenderPublic: Boolean)
