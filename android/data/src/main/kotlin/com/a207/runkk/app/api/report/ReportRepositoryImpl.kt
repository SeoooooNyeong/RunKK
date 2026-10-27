package com.a207.runkk.app.api.report

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.enumByName
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.report.ReportReason
import com.a207.runkk.app.domain.report.ReportRepository
import com.a207.runkk.app.domain.report.ReportStatus
import com.a207.runkk.app.domain.report.ReportTarget
import javax.inject.Inject

/**
 * 콘텐츠 신고 포트를 REST로 구현한다(6.1.14 api.report, FR-214, FR-215, D-104). 앱이 모르는 접수 상태는 성공으로 보지 않고
 * 해독할 수 없는 응답(Server(0))으로 본다(신고가 됐는지 모르는 채 화면을 고르지 않는다).
 */
internal class ReportRepositoryImpl
@Inject
constructor(private val api: ReportApi) : ReportRepository {
    override suspend fun report(target: ReportTarget, reason: ReportReason, detail: String?): Outcome<ReportStatus> =
        ApiErrorMapper.call {
            val reply = api.submit(ReportRequest(target.kind.name, target.targetId, reason.name, detail))
            requireNotNull(enumByName<ReportStatus>(reply.status)) { "모르는 신고 상태 ${reply.status}" }
        }
}
