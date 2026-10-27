package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.crew.CrewRunResult

data class CrewRunResultUiState(
    val isLoading: Boolean = true,
    val result: CrewRunResult? = null,
    val error: UiText? = null
) {
    val finishedCount: Int get() = result?.finishers?.count { it.rank != null } ?: 0
}
