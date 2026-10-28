package com.a207.runkk.app.core.designsystem

/** 신고 시트의 콜백 묶음(Composable 매개변수 4개 기준, AN5-D-3). */
data class ReportSheetCallbacks(
    val onSelect: (Int) -> Unit = {},
    val onDetail: (String) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onDismiss: () -> Unit = {}
)
