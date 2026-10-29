package com.a207.runkk.app.ui.runimal

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.domain.runimal.RunimalTypeDetail

data class RunimalTypeUiState(
    /** 불러오는 유형(서버 RunimalType 이름). */
    val type: String? = null,
    val isLoading: Boolean = true,
    val detail: RunimalTypeDetail? = null,
    val error: UiText? = null
) {
    /** "러너 중 n%가 이 유형이에요". 비율이 없으면(확정자 없음·계산 전) null이라 줄을 그리지 않는다. */
    val shareLabel: UiText?
        get() = detail?.sharePercent?.let { uiText(R.string.runimal_type_share, it) }
}
