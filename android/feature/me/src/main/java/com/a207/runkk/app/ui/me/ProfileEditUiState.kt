package com.a207.runkk.app.ui.me

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.codePointLength
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Gender

/**
 * 프로필 편집(G-2, D-287, FR-007·FR-012): 닉네임 2~20자, 한줄 소개 40자, 나이대·성별 공개 스위치 하나씩. 사진 칸과 프로필 공개 하나는
 * v0.14에서 지웠다. 나이대([ageBand])는 보이기만 하고, 성별([gender])은 고를 수 있다(여성이면 기본 캐릭터가 여자, 2026-10-30).
 * 맨 위 아바타 얼굴([faceKey], [faceLayers])도 보기만 한다(R2-04, D86).
 */
data class ProfileEditUiState(
    val isLoading: Boolean = true,
    val faceKey: String? = null,
    val faceLayers: List<FaceLayer> = emptyList(),
    val nickname: String = "",
    val intro: String = "",
    val ageBand: String? = null,
    /** 출생연도. null이면 바꾸지 않는다. */
    val birthYear: Int? = null,
    val gender: Gender? = null,
    val isAgePublic: Boolean = false,
    val isGenderPublic: Boolean = false,
    val isSaving: Boolean = false,
    val error: UiText? = null
) {
    val canSave: Boolean get() = !isLoading && !isSaving && nickname.trim().codePointLength() in NICKNAME_LENGTH

    companion object {
        val NICKNAME_LENGTH = 2..20
        const val INTRO_MAX = 40
    }
}
