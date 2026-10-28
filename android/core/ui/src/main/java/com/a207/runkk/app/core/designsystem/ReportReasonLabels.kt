package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * 신고 사유 다섯 가지의 라벨(D-186, FR-214). core.ui는 domain을 모르므로 서버·domain ReportReason 이름 순서([ORDER])의 인덱스로
 * 찾는다. ui.chat·ui.crew 시험이 ReportReason.entries 순서가 [ORDER]와 같은지 지킨다.
 */
object ReportReasonLabels {
    val ORDER: List<String> = listOf("ABUSE", "SEXUAL", "SPAM", "PRIVACY", "OTHER")

    private val LABELS =
        listOf(
            R.string.core_report_reason_abuse,
            R.string.core_report_reason_sexual,
            R.string.core_report_reason_spam,
            R.string.core_report_reason_privacy,
            R.string.core_report_reason_other
        )

    @Composable
    fun labels(): List<String> = LABELS.map { stringResource(it) }
}

/** 화면이 정하는 제목·대상 한 줄·상세 칸 라벨에 공용 문구를 채운 [ReportDialogTexts]. */
@Composable
fun reportDialogTexts(title: String, detailLabel: String, subtitle: String? = null): ReportDialogTexts =
    ReportDialogTexts(
        title = title,
        detailLabel = detailLabel,
        reasonsLabel = stringResource(R.string.core_report_reason_group),
        submitLabel = stringResource(R.string.core_report_submit),
        dismissLabel = stringResource(R.string.core_fields_cancel),
        subtitle = subtitle
    )
