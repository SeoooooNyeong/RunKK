package com.a207.runkk.app.api.record

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.api.core.TokenProvider
import com.a207.runkk.app.api.core.jwtOf
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.Evaluation
import com.a207.runkk.app.domain.record.NewRecordMark
import com.a207.runkk.app.domain.record.RecordBattleType
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.record.RecordVerdict
import com.a207.runkk.app.domain.record.RunSummary
import com.a207.runkk.app.domain.record.ShapePoint
import java.time.Duration
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import retrofit2.create

/** api.record v0.14 매퍼(D-279, D-280, D-284, D-289): 목록 행 새 필드, 월 요약, 상세 삭제·평가·날씨·정지, 삭제 요청. */
class RecordRepositoryV014Test {
    private val api = ApiTestServer()
    private val tokens =
        object : TokenProvider {
            override fun accessToken(): String? = jwtOf("7")

            override fun refreshToken(): String? = null

            override fun update(access: String, refresh: String) = Unit

            override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
        }
    private val repository = RecordRepositoryImpl(api.retrofit.create(), SelfMember(tokens))

    @AfterTest
    fun tearDown() = api.close()

    private suspend fun runs(): List<RunSummary> =
        repository.runs(RecordRepository.SELF, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).value().runs

    @Test
    fun `TC-9852 기록 목록 행은 대결 종류·승패·보류·신기록 종류·경로 모양을 읽는다`() = runTest {
        api.reply(
            """{"ownerId":7,"runs":[{"recordId":"r-1","startedAt":"2026-10-20T07:00:00+09:00","distanceMeters":5010,
               |"elapsedSeconds":1620,"averagePaceSecondsPerKm":323,"valid":true,"mode":"BATTLE","cardKind":"BATTLE_RUN",
               |"battleType":"FRIEND","verdict":"LOSE","pending":false,"newRecord":false,"newRecords":[],
               |"routeShape":[[0.0,1.0],[0.5,0.25],[1.0,0.0]]},
               |{"recordId":"r-2","startedAt":"2026-10-19T07:00:00+09:00","distanceMeters":10000,"elapsedSeconds":3600,
               |"valid":true,"mode":"FREE","cardKind":"FREE_RUN","battleType":null,"verdict":null,"pending":false,
               |"newRecord":true,"newRecords":[{"kind":"LONGEST","distance":null},{"kind":"BASE_DISTANCE","distance":"KM10"}],
               |"routeShape":null}]}
            """.trimMargin()
        )

        val runs = runs()

        assertEquals(RecordBattleType.FRIEND, runs[0].battleType)
        assertEquals(RecordVerdict.LOSE, runs[0].verdict)
        assertFalse(runs[0].isPending)
        assertEquals(listOf(ShapePoint(0f, 1f), ShapePoint(0.5f, 0.25f), ShapePoint(1f, 0f)), runs[0].routeShape)
        assertEquals(null, runs[1].battleType)
        assertTrue(runs[1].isNewRecord)
        assertEquals(
            listOf(
                NewRecordMark(NewRecordMark.Kind.LONGEST, null),
                NewRecordMark(NewRecordMark.Kind.BASE_DISTANCE, "KM10")
            ),
            runs[1].newRecords
        )
        assertEquals(emptyList(), runs[1].routeShape)
    }

    @Test
    fun `보류 중인 대결 행은 승패 없이 보류로 읽고 모르는 종류·승패·신기록 종류는 버린다`() = runTest {
        api.reply(
            """{"runs":[{"recordId":"r-1","startedAt":"2026-10-20T07:00:00+09:00","distanceMeters":5010,
               |"elapsedSeconds":1620,"valid":true,"mode":"BATTLE","cardKind":"BATTLE_RUN","battleType":"RANDOM",
               |"verdict":null,"pending":true},
               |{"recordId":"r-2","startedAt":"2026-10-20T07:00:00+09:00","distanceMeters":5010,
               |"elapsedSeconds":1620,"valid":true,"mode":"BATTLE","cardKind":"BATTLE_RUN","battleType":"NEW_TYPE",
               |"verdict":"NEW_VERDICT","newRecords":[{"kind":"NEW_KIND","distance":null}],"routeShape":[[0.1],[0.2,0.3]]}]}
            """.trimMargin()
        )

        val runs = runs()

        assertEquals(RecordBattleType.RANDOM, runs[0].battleType)
        assertEquals(null, runs[0].verdict)
        assertTrue(runs[0].isPending)
        assertEquals(null, runs[1].battleType)
        assertEquals(null, runs[1].verdict)
        assertEquals(emptyList(), runs[1].newRecords)
        assertEquals(listOf(ShapePoint(0.2f, 0.3f)), runs[1].routeShape)
    }

    @Test
    fun `TC-9743 옛 서버 기록 목록 행은 새 필드 없이 기본값으로 해독된다`() = runTest {
        api.reply(
            """{"runs":[{"recordId":"r-1","startedAt":"2026-10-20T07:00:00+09:00","distanceMeters":5010,
               |"elapsedSeconds":1620,"valid":false,"mode":"FREE","cardKind":"FREE_RUN"}]}
            """.trimMargin()
        )

        val run = runs().single()

        assertEquals(null, run.battleType)
        assertEquals(null, run.verdict)
        assertFalse(run.isPending)
        assertFalse(run.isNewRecord)
        assertEquals(emptyList(), run.newRecords)
        assertEquals(emptyList(), run.routeShape)
    }

    @Test
    fun `TC-9731 대시보드는 그 달 유효 러닝 횟수·거리를 읽고 옛 서버면 null이다`() = runTest {
        api.reply("""{"todayMeters":0,"totalMeters":0,"streakDays":4,"monthRuns":12,"monthMeters":61300}""")
        api.reply("""{"todayMeters":0,"totalMeters":0}""")

        val dashboard = repository.dashboard(LocalDate.of(2026, 10, 1)).value()
        val old = repository.dashboard(LocalDate.of(2026, 10, 1)).value()

        assertEquals(12, dashboard.monthRuns)
        assertEquals(Distance(61_300), dashboard.monthDistance)
        assertEquals(null, old.monthRuns)
        assertEquals(null, old.monthDistance)
    }

    @Test
    fun `TC-9852 기록 상세는 삭제 가능·평가·날씨·정지 구간을 읽는다`() = runTest {
        api.reply(
            """{"summary":{"recordId":"r-1","startedAt":"2026-10-20T07:00:00+09:00","distanceMeters":5010,"elapsedSeconds":1620,
               |"valid":false,"mode":"FREE","cardKind":"FREE_RUN"},"owner":true,"calories":320,"recordVisibility":"CREW",
               |"temperatureC":12,"sky":"CLEAR","pauses":[{"from":"2026-10-20T07:10:00+09:00","to":"2026-10-20T07:12:00+09:00"},
               |{"from":"2026-10-20T07:20:00+09:00","to":"2026-10-20T07:21:00+09:00"}],"pausedSeconds":180,"deletable":true,
               |"evaluation":{"kind":"REEVALUATION","tier":"GOLD","validRuns":20,"runimalType":"COMMUTER_TURTLE"}}
            """.trimMargin()
        )

        val detail = repository.run("r-1").value()

        assertTrue(detail.isDeletable)
        assertEquals(Evaluation(Evaluation.Kind.REEVALUATION, "GOLD", 20, "COMMUTER_TURTLE"), detail.evaluation)
        assertEquals(12, detail.temperatureC)
        assertEquals("CLEAR", detail.sky)
        assertEquals(2, detail.pauseCount)
        assertEquals(Duration.ofSeconds(180), detail.paused)
        assertEquals("CREW", detail.recordVisibility)
    }

    @Test
    fun `TC-9737 첫 배치 평가는 유형 없이 읽고 평가가 없거나 티어가 없으면 null이다`() = runTest {
        val head =
            """{"summary":{"recordId":"r-1","startedAt":"2026-10-20T07:00:00+09:00","distanceMeters":5010,"elapsedSeconds":1620,
               |"valid":true,"mode":"FREE","cardKind":"FREE_RUN"},"deletable":false,
            """.trimMargin()
        api.reply("""$head"evaluation":{"kind":"FIRST_PLACEMENT","tier":"SILVER","validRuns":3,"runimalType":null}}""")
        api.reply("""$head"evaluation":null}""")
        api.reply("""$head"evaluation":{"kind":"REEVALUATION","tier":null,"validRuns":10}}""")

        assertEquals(
            Evaluation(Evaluation.Kind.FIRST_PLACEMENT, "SILVER", 3, null),
            repository.run("r-1").value().evaluation
        )
        assertEquals(null, repository.run("r-1").value().evaluation)
        assertEquals(null, repository.run("r-1").value().evaluation)
    }

    @Test
    fun `TC-9735 deletable이 없으면 본인 기록이고 집계 제외일 때만 삭제할 수 있다`() = runTest {
        fun detail(valid: Boolean, extra: String) =
            """{"summary":{"recordId":"r","startedAt":"2026-10-20T07:00:00+09:00","distanceMeters":10,"elapsedSeconds":10,
               |"valid":$valid,"mode":"FREE","cardKind":"FREE_RUN"}$extra}
            """.trimMargin()
        api.reply(detail(valid = false, extra = ""))
        api.reply(detail(valid = true, extra = ""))
        api.reply(detail(valid = false, extra = ""","owner":false"""))
        api.reply(detail(valid = false, extra = ""","deletable":false"""))
        api.reply(detail(valid = true, extra = ""","deletable":true"""))

        assertTrue(repository.run("r").value().isDeletable)
        assertFalse(repository.run("r").value().isDeletable)
        assertFalse(repository.run("r").value().isDeletable)
        assertFalse(repository.run("r").value().isDeletable)
        assertTrue(repository.run("r").value().isDeletable)
    }

    @Test
    fun `TC-9743 옛 서버 기록 상세는 평가·날씨·정지 구간 없이 해독된다`() = runTest {
        api.reply(
            """{"summary":{"recordId":"r-1","startedAt":"2026-10-20T07:00:00+09:00","distanceMeters":5010,"elapsedSeconds":1620,
               |"valid":true,"mode":"FREE","cardKind":"FREE_RUN"}}
            """.trimMargin()
        )

        val detail = repository.run("r-1").value()

        assertEquals(null, detail.evaluation)
        assertEquals(null, detail.temperatureC)
        assertEquals(null, detail.sky)
        assertEquals(0, detail.pauseCount)
        assertEquals(Duration.ZERO, detail.paused)
        assertEquals(null, detail.recordVisibility)
    }

    @Test
    fun `TC-9736 기록 삭제는 DELETE runs id로 보내고 204면 성공이다`() = runTest {
        api.replyEmpty(204)

        assertEquals(Outcome.Success(Unit), repository.delete("r-1"))

        val request = api.take()
        assertEquals("DELETE /api/v1/runs/r-1", "${request.method} ${request.url.encodedPath}")
    }

    @Test
    fun `TC-9736 기록 삭제 거부는 본문 code로 가른다(D-290)`() = runTest {
        api.reply("""{"code":"RECORD_VALID_NOT_DELETABLE","message":"…"}""", code = 409)
        api.reply("""{"code":"RECORD_NOT_FOUND","message":"…"}""", code = 409)
        api.reply("""{"code":"PERMISSION_DENIED","message":"…"}""", code = 403)

        assertEquals(Outcome.Failure(AppError.Rejected("RECORD_VALID_NOT_DELETABLE")), repository.delete("r-1"))
        assertEquals(Outcome.Failure(AppError.Rejected("RECORD_NOT_FOUND")), repository.delete("r-1"))
        assertEquals(Outcome.Failure(AppError.Rejected("PERMISSION_DENIED")), repository.delete("r-1"))
    }
}
