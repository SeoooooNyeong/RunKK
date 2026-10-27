package com.a207.runkk.app.ui.me

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.codePointLength

/**
 * 프로필 편집(F3, FR-007): 닉네임 2~20자, 소개 40자, 나이대·성별·소개 공개(설정 PROFILE_VISIBILITY). 성향 태그는 편집 항목이 아니다(D-91).
 * [isProfilePublic]이 null이면 설정을 아직 읽지 못했다(스위치를 뺀다).
 */
data class ProfileEditUiState(
    val isLoading: Boolean = true,
    val nickname: String = "",
    val intro: String = "",
    val isProfilePublic: Boolean? = null,
    val isSaving: Boolean = false,
    val error: UiText? = null
) {
    val canSave: Boolean get() = !isLoading && !isSaving && nickname.trim().codePointLength() in NICKNAME_LENGTH

    companion object {
        val NICKNAME_LENGTH = 2..20
        const val INTRO_MAX = 40
    }
}
