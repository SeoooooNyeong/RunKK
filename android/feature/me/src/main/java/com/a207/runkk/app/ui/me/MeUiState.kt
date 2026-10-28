package com.a207.runkk.app.ui.me

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.domain.account.Profile

data class MeUiState(
    val isLoading: Boolean = true,
    val profile: Profile? = null,
    /** 프로필 칸 안의 셀카 얼굴 부위 그림. 비어 있으면 기본 얼굴·자리 그림. */
    val faceLayers: List<FaceLayer> = emptyList(),
    val error: UiText? = null
)
