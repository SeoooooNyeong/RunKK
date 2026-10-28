package com.a207.runkk.app.api.report

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.report.ReportReason
import com.a207.runkk.app.domain.report.ReportStatus
import com.a207.runkk.app.domain.report.ReportTarget
import com.a207.runkk.app.domain.report.ReportTargetKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import retrofit2.create

/** api.report(6.1.14, F.2.2 신고, FR-214, FR-215, D-104). S3 구현 전이라 05-03·S0 DTO 기준. */
class ReportRepositoryImplTest {
    private val api = ApiTestServer()
    private val repository = ReportRepositoryImpl(api.retrofit.create())

    @AfterTest
    fun tearDown() = api.close()

    @Test
    fun `신고는 대상·사유·내용을 보내고 접수 상태를 돌려준다`() = runTest {
        api.reply("""{"reportId":31,"status":"AUTO_HIDDEN"}""", code = 201)
        api.reply("""{"reportId":32,"status":"RECEIVED"}""", code = 201)

        val hidden = repository.report(ReportTarget(ReportTargetKind.CHAT_MESSAGE, 101), ReportReason.OTHER, "광고 도배")
        val received = repository.report(ReportTarget(ReportTargetKind.BOARD, 7), ReportReason.SPAM, null)

        assertEquals(Outcome.Success(ReportStatus.AUTO_HIDDEN), hidden)
        assertEquals(Outcome.Success(ReportStatus.RECEIVED), received)
        val first = api.take()
        assertEquals("POST /api/v1/reports", "${first.method} ${first.url.encodedPath}")
        assertEquals(
            Json.parseToJsonElement(
                """{"targetKind":"CHAT_MESSAGE","targetId":101,"reason":"OTHER","detail":"광고 도배"}"""
            ),
            Json.parseToJsonElement(first.body!!.utf8())
        )
        assertEquals(
            Json.parseToJsonElement("""{"targetKind":"BOARD","targetId":7,"reason":"SPAM"}"""),
            Json.parseToJsonElement(api.take().body!!.utf8())
        )
    }

    @Test
    fun `거부는 사유 코드로, 모르는 접수 상태는 해독 실패로 돌려준다`() = runTest {
        api.reply("""{"code":"ALREADY_REPORTED","message":"이미 신고함"}""", code = 409)
        api.reply("""{"code":"BAD_REQUEST","message":"내용 필요"}""", code = 400)
        api.reply("""{"reportId":33,"status":"ESCALATED"}""", code = 201)
        val target = ReportTarget(ReportTargetKind.CHEER, 1)

        assertEquals(
            Outcome.Failure(AppError.Rejected("ALREADY_REPORTED")),
            repository.report(target, ReportReason.ABUSE, null)
        )
        assertEquals(
            Outcome.Failure(AppError.Rejected("BAD_REQUEST")),
            repository.report(target, ReportReason.OTHER, null)
        )
        assertEquals(Outcome.Failure(AppError.Server(0)), repository.report(target, ReportReason.ABUSE, null))
    }
}
