package com.a207.runkk.app.ui.record

import java.time.LocalDate

/** 기록 화면 행동 묶음(탭·달 이동·날짜·기록 열기·다시 읽기). */
class RecordListActions(
    val onBack: () -> Unit = {},
    val onViewSelected: (RecordView) -> Unit = {},
    val onPreviousMonth: () -> Unit = {},
    val onNextMonth: () -> Unit = {},
    val onDateSelected: (LocalDate) -> Unit = {},
    val onRun: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRetryRecent: () -> Unit = {}
)
