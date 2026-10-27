package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.record.RecordDetail

data class RecordDetailUiState(
    val isLoading: Boolean = true,
    val recordId: String? = null,
    val detail: RecordDetail? = null,
    val error: UiText? = null
)
