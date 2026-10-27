package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText

/** 글쓰기 화면 틀의 값 묶음(Composable 매개변수 4개 기준, AN5-D-3): 제목·올리기 단추·저장 중·오류·안내. */
internal data class WriteScaffoldState(
    val title: String,
    val submitLabel: String,
    val isSubmitEnabled: Boolean,
    val isSaving: Boolean,
    val error: UiText?,
    val message: UiText? = null
)
