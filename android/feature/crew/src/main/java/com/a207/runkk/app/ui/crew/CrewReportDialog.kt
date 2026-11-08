package com.a207.runkk.app.ui.crew

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.a207.runkk.app.core.designsystem.ReportDialogState
import com.a207.runkk.app.core.designsystem.RunkkReportDialog
import com.a207.runkk.app.core.designsystem.reportDialogTexts
import com.a207.runkk.app.domain.report.ReportReason

/** 크루 콘텐츠 신고 시트(FR-214, 와이어프레임 M10): 사유 다섯 가지, 상세(기타는 필수, 200자). 상태와 콜백만 받는다(AN5-D-4). */
@Composable
internal fun CrewReportDialog(state: CrewReportUiState, actions: CrewReportActions) {
    if (state.target == null) return
    val reasons = ReportReason.entries
    RunkkReportDialog(
        texts = reportDialogTexts(
            title = stringResource(R.string.crew_report_title),
            detailLabel = stringResource(R.string.crew_report_detail_label),
            subtitle = state.targetKind?.let {
                stringResource(R.string.crew_report_subtitle, state.targetAuthor, stringResource(it.kindLabelRes()))
            }
        ),
        state = ReportDialogState(
            selectedIndex = state.reason?.let(reasons::indexOf),
            detail = state.detail,
            isDetailMissing = state.isDetailMissing,
            isSubmitEnabled = state.canSubmit,
            detailMax = CrewReportUiState.DETAIL_MAX
        ),
        onSelect = { actions.onReason(reasons[it]) },
        onDetail = actions.onDetail,
        onSubmit = actions.onSubmit,
        onDismiss = actions.onDismiss
    )
}
