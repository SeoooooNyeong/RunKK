package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.domain.report.ReportReason

/** 신고 대화상자 콜백 묶음(AN5-D-4). Route가 CrewReportViewModel의 연산으로 채운다. */
internal class CrewReportActions(
    val onReason: (ReportReason) -> Unit,
    val onDetail: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onDismiss: () -> Unit
)
