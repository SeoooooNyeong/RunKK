package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.Immutable

/**
 * 신고 시트 상태 묶음(D-186). [selectedIndex]는 [ReportReasonLabels.ORDER] 순서의 고른 사유(없으면 null),
 * [isDetailMissing]은 기타를 고르고 상세를 비웠는가, [isSubmitEnabled]는 보낼 수 있는가, [detailMax]는 상세 글자 수 상한이다.
 */
@Immutable
data class ReportDialogState(
    val selectedIndex: Int?,
    val detail: String,
    val isDetailMissing: Boolean,
    val isSubmitEnabled: Boolean,
    val detailMax: Int = DETAIL_MAX
) {
    companion object {
        /** 신고 상세 상한(FR-214 200자 제안 값, 코드 포인트). */
        const val DETAIL_MAX = 200
    }
}
