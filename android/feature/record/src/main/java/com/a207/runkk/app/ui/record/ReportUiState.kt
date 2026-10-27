package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.record.Report

data class ReportUiState(val isLoading: Boolean = true, val report: Report? = null, val error: UiText? = null) {
    /** 코멘트가 아직 없으면 "코멘트 준비 중"(FR-066). */
    val commentText: UiText
        get() = report?.comment?.let(UiText::Plain) ?: UiText.Res(R.string.record_report_comment_pending)

    /** 요약 한 줄. 없으면 서버 안내 문구(유효 러닝 없음 등), 그것도 없으면 기본 문구. */
    val summaryText: UiText
        get() = (report?.summary ?: report?.message)?.let(UiText::Plain)
            ?: UiText.Res(R.string.record_report_summary_empty)
}
