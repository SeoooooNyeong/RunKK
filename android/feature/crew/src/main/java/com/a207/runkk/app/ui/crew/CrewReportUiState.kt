package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.codePointLength
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.report.ReportReason
import com.a207.runkk.app.domain.report.ReportTarget

/** 크루 콘텐츠 신고 시트(FR-214, 와이어프레임 M10) 상태. [target]이 있으면 대화상자가 열린다. */
data class CrewReportUiState(
    val target: ReportTarget? = null,
    /** 신고할 글의 작성자와 종류(시트의 대상 한 줄). */
    val targetAuthor: String = "",
    val targetKind: PostList.Kind? = null,
    val reason: ReportReason? = null,
    val detail: String = "",
    val isSubmitting: Boolean = false,
    val message: UiText? = null
) {
    /** 기타 사유면 상세 내용이 필요하다(앞뒤 공백 제외 1~200자, D-149). */
    val isDetailMissing: Boolean get() = reason == ReportReason.OTHER && detail.isBlank()

    val canSubmit: Boolean get() = target != null && reason != null && !isDetailMissing && !isSubmitting

    /** 상세 글자 수(코드 포인트, D-143). */
    val detailLength: Int get() = detail.codePointLength()

    companion object {
        const val DETAIL_MAX = 200
    }
}
