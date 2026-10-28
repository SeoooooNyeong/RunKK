package com.a207.runkk.app.ui.crew

/** 공지 쓰기 화면의 입력 묶음. */
data class NoticeWriteActions(
    val onBack: () -> Unit = {},
    val onBody: (String) -> Unit = {},
    val onPinned: (Boolean) -> Unit = {},
    val onSubmit: () -> Unit = {}
)
