package com.a207.runkk.app.ui.runimal

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.runimal.AxisLean
import com.a207.runkk.app.domain.runimal.RunimalTypeBoard

data class RunimalTypesUiState(
    val isLoading: Boolean = true,
    val board: RunimalTypeBoard? = null,
    val error: UiText? = null
) {
    /** 지금 유형이 기운 쪽(머리 범례 굵게). 유형이 없으면 빈 맵. */
    val currentAxes: Map<String, AxisLean>
        get() {
            val b = board ?: return emptyMap()
            return b.types.firstOrNull { it.type == b.current }?.axes.orEmpty()
        }

    /** 한 번도 확정받지 않은 유형은 흐리게 보인다(D-285). 임시 유형은 MY Runimal에서만 보이므로(D56) 여기서는 따로 두지 않는다. */
    fun isDimmed(entry: RunimalTypeBoard.Entry): Boolean = !entry.isEverConfirmed
}
