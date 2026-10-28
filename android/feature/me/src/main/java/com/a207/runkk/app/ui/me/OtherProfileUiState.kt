package com.a207.runkk.app.ui.me

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.domain.account.Profile

/** 다른 러너 프로필(F8). 공개 수준에 따라 서버가 걸러 보낸 항목만 있다(D-52). */
data class OtherProfileUiState(
    val memberId: Long? = null,
    val isLoading: Boolean = true,
    val profile: Profile? = null,
    val faceLayers: List<FaceLayer> = emptyList(),
    val error: UiText? = null
)
