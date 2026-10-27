package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.avatar.AvatarBackgrounds

/** 랜드마크 배경 고르기(FR-201, FR-202) 상태. [backgrounds]가 null이면 아직 읽지 못했다. */
data class AvatarBackgroundUiState(
    val isLoading: Boolean = true,
    val backgrounds: AvatarBackgrounds? = null,
    /** 지금 고르는 중인 배경 키(기본 배경은 [DEFAULT_KEY]). */
    val choosingKey: String? = null,
    val message: UiText? = null,
    val error: UiText? = null
) {
    /** 고른 배경 키. null이면 기본 배경이다. */
    val selectedKey: String? get() = backgrounds?.selectedKey

    companion object {
        /** 기본 배경을 고르는 중일 때 [choosingKey]에 넣는 표지(서버 키와 겹치지 않는다). */
        const val DEFAULT_KEY = "__default__"
    }
}
