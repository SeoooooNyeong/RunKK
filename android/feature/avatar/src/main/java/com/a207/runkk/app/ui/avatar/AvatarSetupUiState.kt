package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.designsystem.UiText

data class AvatarSetupUiState(
    val isLoading: Boolean = true,
    /** 오늘 셀카 아바타를 만들 수 있는가(하루 1회, FR-157). 모르면 참으로 두고 서버가 거부한다. */
    val canCreateSelfie: Boolean = true,
    val selected: DefaultCharacter? = null,
    /** 지금 얼굴 키(미리보기, 고른 캐릭터가 없을 때). */
    val faceKey: String? = null,
    val isWorking: Boolean = false,
    val error: UiText? = null
)
