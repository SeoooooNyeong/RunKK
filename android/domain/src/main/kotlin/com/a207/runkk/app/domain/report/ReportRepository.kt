package com.a207.runkk.app.domain.report

import com.a207.runkk.app.core.model.Outcome

/**
 * 콘텐츠 신고 포트(POST /reports, FR-214, FR-215, D-104). 구현 api.report.ReportRepositoryImpl. ui.crew·ui.chat의 신고 대화상자가
 * 부른다. [reason]이 OTHER면 [detail]이 필요하다(200자, E-v011-3 잠정). 검사는 서버가 하고 거부는 AppError.Rejected
 * (ALREADY_REPORTED, REPORT_TARGET_NOT_FOUND 등)로 온다. 성공하면 접수 뒤 상태(RECEIVED 또는 AUTO_HIDDEN)를 돌려준다.
 */
interface ReportRepository {
    suspend fun report(target: ReportTarget, reason: ReportReason, detail: String?): Outcome<ReportStatus>
}
