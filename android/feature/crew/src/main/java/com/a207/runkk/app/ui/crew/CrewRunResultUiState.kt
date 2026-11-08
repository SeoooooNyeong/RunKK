package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.record.RecordDetail

/** 크루런 결과(F-16)와 내 기록 상세(F-16b, [detail]은 내 기록이 올라가 읽은 뒤에 있다). */
data class CrewRunResultUiState(
    val isLoading: Boolean = true,
    val result: CrewRunResult? = null,
    val detail: RecordDetail? = null,
    val error: UiText? = null
)
