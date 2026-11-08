package com.a207.runkk.app.ui.record.runbody

import com.a207.runkk.app.domain.record.RunSummary

/**
 * 런바디 화면 콜백 묶음. Route가 RunBodyViewModel 연산으로 채운다. [onCoach]가 null이면 "AI 코치" 버튼을 그리지 않는다
 * (코치 어댑터·서버 중계 #241이 없어 코치 화면을 경로에 등록하지 않았다, D-451·D-474).
 */
class RunBodyActions(
    val onBack: () -> Unit = {},
    val onTab: (RunBodyTab) -> Unit = {},
    val onRetryReport: () -> Unit = {},
    val onRetryRuns: () -> Unit = {},
    val onOpenPick: () -> Unit = {},
    val onPick: (RunSummary) -> Unit = {},
    val onRetrySession: () -> Unit = {},
    val onOpenDetail: () -> Unit = {},
    val onPreviousMonth: () -> Unit = {},
    val onNextMonth: () -> Unit = {},
    val onRetryMonth: () -> Unit = {},
    val onCoach: (() -> Unit)? = null
)
