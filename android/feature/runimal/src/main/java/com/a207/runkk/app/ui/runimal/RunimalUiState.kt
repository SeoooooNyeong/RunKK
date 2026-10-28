package com.a207.runkk.app.ui.runimal

import android.graphics.Bitmap
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.domain.runimal.RunimalStatus

data class RunimalUiState(
    val isLoading: Boolean = true,
    val status: RunimalStatus? = null,
    /** 공유할 카드 이미지(공유를 처음 누를 때 만든다). */
    val cardImage: Bitmap? = null,
    val isSharing: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
) {
    /** 다음 판정까지 남은 횟수 문구(FR-154): 유형 전이면 "임시 유형까지 n회", 뒤면 "다음 확정까지 n회". */
    val remainingLabel: UiText?
        get() {
            val s = status ?: return null
            val n = s.remainingRuns ?: return null
            return if (s.type == null) {
                uiText(R.string.runimal_remaining_provisional, n)
            } else {
                uiText(R.string.runimal_remaining_confirm, n)
            }
        }

    /** 임시·확정 유형이 있을 때만 공유한다(FR-168). */
    val canShare: Boolean get() = status?.type != null
}
