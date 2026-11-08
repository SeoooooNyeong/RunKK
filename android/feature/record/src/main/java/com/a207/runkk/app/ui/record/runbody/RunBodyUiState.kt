package com.a207.runkk.app.ui.record.runbody

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.record.Report
import com.a207.runkk.app.domain.record.RunSummary
import com.a207.runkk.app.domain.runbody.RunBodyMonth
import com.a207.runkk.app.domain.runbody.RunBodyRunScore
import com.a207.runkk.app.domain.runbody.RunBodyTrend
import com.a207.runkk.app.ui.record.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 런바디 화면 상태(FR-066·FR-067·FR-266·FR-267). 탭마다 처음 고를 때 읽는다: 요약은 리포트(GET /report)와 최근 10회 변화
 * ([trend], G-14), 러닝별은 최근 기록과 고른 기록의 분석, 러닝 선택(G-30)의 러닝 점수([scores]), 월간은 [month]의 집계다.
 */
data class RunBodyUiState(
    val today: LocalDate,
    val zone: ZoneId,
    val tab: RunBodyTab = RunBodyTab.SUMMARY,
    val page: RunBodyPage = RunBodyPage.MAIN,
    val isReportLoading: Boolean = true,
    val report: Report? = null,
    val reportError: UiText? = null,
    val isRunsLoading: Boolean = false,
    val runs: List<RunSummary> = emptyList(),
    val runsError: UiText? = null,
    val selectedRecordId: String? = null,
    val session: RunBodySessionState = RunBodySessionState.Loading,
    val month: YearMonth = YearMonth.from(today),
    val isMonthLoading: Boolean = false,
    val monthData: RunBodyMonth? = null,
    val monthError: UiText? = null,
    val isTrendLoading: Boolean = true,
    val trend: RunBodyTrend? = null,
    val trendError: UiText? = null,
    val period: RunBodyPeriod = RunBodyPeriod.WEEK,
    /** 러닝 선택 목록의 러닝 점수(기록 ID별). 읽지 못했으면 비어 있고 목록은 점수 없이 그린다. */
    val scores: Map<String, RunBodyRunScore> = emptyMap()
) {
    val selectedRun: RunSummary? get() = runs.firstOrNull { it.recordId == selectedRecordId }

    /** 이번 달보다 뒤로는 가지 않는다. */
    val canGoNextMonth: Boolean get() = month < YearMonth.from(today)

    /** 코멘트가 아직 없으면 "코멘트 준비 중"(FR-066, G-33). */
    val commentText: UiText
        get() = report?.comment?.let(UiText::Plain) ?: UiText.Res(R.string.record_report_comment_pending)

    /** 요약 한 줄. 없으면 서버 안내 문구(유효 러닝 없음 등), 그것도 없으면 기본 문구. */
    val summaryText: UiText
        get() = (report?.summary ?: report?.message)?.let(UiText::Plain)
            ?: UiText.Res(R.string.record_report_summary_empty)
}
