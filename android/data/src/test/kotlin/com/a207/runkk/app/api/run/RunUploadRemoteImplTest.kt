package com.a207.runkk.app.api.run

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.core.model.SignalState
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.PauseInterval
import com.a207.runkk.app.domain.run.RoutePoint
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.app.domain.run.UnstableSegment
import com.a207.runkk.app.domain.sync.DisposalOrder
import com.a207.runkk.app.domain.sync.UploadReceipt
import java.time.Duration
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.create

/** api.run(14장 단위(앱 데이터) "api.run의 CompletedRun → RunUploadRequest"): 전송 형식과 파기 지시 매퍼. */
class RunUploadRemoteImplTest {
    private val api = ApiTestServer()
    private val runApi: RunApi = api.retrofit.create()

    @AfterTest
    fun tearDown() = api.close()

    @Test
    fun `CompletedRun을 RunUploadRequest로 바꾼다`() {
        val request = RUN.toRequest()

        assertEquals(
            RunUploadRequest(
                recordId = "0f8e6c1a-4d2b-4c55-9d8e-3b6c1f7a2e90",
                mode = "BATTLE",
                cardKind = "BATTLE_RUN",
                battleId = 42,
                crewRunPostId = null,
                startedAt = "2026-10-20T07:00:00+09:00",
                endedAt = "2026-10-20T07:10:05.5+09:00",
                elapsedMillis = 605_500,
                distanceMeters = 2_012,
                pointCount = 2,
                points =
                    listOf(
                        RunUploadRequest.Point("2026-10-20T07:00:01+09:00", 37.501234, 127.034567, "NORMAL"),
                        RunUploadRequest.Point("2026-10-20T07:00:02+09:00", 37.501301, 127.034601, "UNSTABLE")
                    ),
                unstableSegments =
                    listOf(RunUploadRequest.Segment("2026-10-20T07:01:40+09:00", "2026-10-20T07:01:55+09:00", 42)),
                heartRates = listOf(RunUploadRequest.HeartRate("2026-10-20T07:00:10+09:00", 130)),
                pauses = emptyList(),
                startDevice = "PHONE"
            ),
            request
        )
    }

    @Test
    fun `시작 기기를 서버 StartDevice 이름으로 보낸다(W23, D-378)`() {
        assertEquals("WATCH", RUN.copy(startDevice = RunStartSpec.StartDevice.WATCH).toRequest().startDevice)
        assertEquals("PHONE", RUN.toRequest().startDevice)
    }

    @Test
    fun `TC-6524 정지 구간을 오프셋 시각 from·to로 보내고 종료 시각에 정지 시간을 더한다`() {
        val free =
            RUN.copy(
                mode = RunMode.FREE,
                cardKind = CardKind.FREE_RUN,
                battleId = null,
                startedAt = Instant.parse("2026-10-22T10:00:00Z"),
                elapsed = Duration.ofMinutes(20),
                pauses = listOf(
                    PauseInterval(Instant.parse("2026-10-22T10:10:00Z"), Instant.parse("2026-10-22T10:15:00Z"))
                )
            )

        val json = Json.parseToJsonElement(api.json.encodeToString(RunUploadRequest.serializer(), free.toRequest()))

        assertEquals(
            Json.parseToJsonElement("""[{"from":"2026-10-22T19:10:00+09:00","to":"2026-10-22T19:15:00+09:00"}]"""),
            json.jsonObject["pauses"]
        )
        assertEquals("2026-10-22T19:25:00+09:00", json.jsonObject["endedAt"]!!.jsonPrimitive.content, "20분 운동 + 5분 정지")
        assertEquals("1200000", json.jsonObject["elapsedMillis"]!!.jsonPrimitive.content, "elapsedMillis는 운동 시간")
        val empty = Json.parseToJsonElement(api.json.encodeToString(RunUploadRequest.serializer(), RUN.toRequest()))
        assertEquals(Json.parseToJsonElement("[]"), empty.jsonObject["pauses"], "정지 없음은 빈 배열")
    }

    @Test
    fun `전송 JSON에는 위치·심박이 그 항목에만 있다`() {
        val json = api.json.encodeToString(RunUploadRequest.serializer(), RUN.toRequest())
        val keys = Json.parseToJsonElement(json).let { it as kotlinx.serialization.json.JsonObject }.keys

        assertEquals(
            setOf(
                "recordId",
                "mode",
                "cardKind",
                "battleId",
                "startedAt",
                "endedAt",
                "elapsedMillis",
                "distanceMeters",
                "pointCount",
                "points",
                "unstableSegments",
                "heartRates",
                "pauses",
                "startDevice"
            ),
            keys
        )
    }

    @Test
    fun `upload는 POST runs로 보내고 이미 저장됨을 읽는다`() = runTest {
        api.reply("""{"recordId":"${RUN.recordId}","status":"ALREADY_STORED","valid":true}""")

        val receipt = RunUploadRemoteImpl(runApi).upload(RUN)

        assertEquals(Outcome.Success(UploadReceipt(RUN.recordId, isAlreadySaved = true)), receipt)
        val request = api.take()
        assertEquals("POST /api/v1/runs", "${request.method} ${request.url.encodedPath}")
    }

    @Test
    fun `새로 저장된 기록은 alreadySaved가 아니다`() = runTest {
        api.reply("""{"recordId":"${RUN.recordId}","status":"STORED","valid":true}""")

        assertEquals(
            Outcome.Success(UploadReceipt(RUN.recordId, isAlreadySaved = false)),
            RunUploadRemoteImpl(runApi).upload(RUN)
        )
    }

    @Test
    fun `시각은 서버 OffsetDateTime이 받는 오프셋 형식이고 구간은 from·to다`() {
        val json = api.json.encodeToString(RunUploadRequest.serializer(), RUN.toRequest())

        assertEquals(true, """"startedAt":"2026-10-20T07:00:00+09:00"""" in json, json)
        assertEquals(
            true,
            """"unstableSegments":[{"from":"2026-10-20T07:01:40+09:00","to":"2026-10-20T07:01:55+09:00"""" in json,
            json
        )
    }

    @Test
    fun `늦은 심박은 기록의 heart-rates로 보낸다`() = runTest {
        api.replyEmpty()

        val result =
            RunUploadRemoteImpl(runApi).appendHeartRates(
                RUN.recordId,
                listOf(HeartRateSample(Instant.parse("2026-10-19T22:15:00Z"), 150))
            )

        assertEquals(Outcome.Success(Unit), result)
        val request = api.take()
        assertEquals("/api/v1/runs/${RUN.recordId}/heart-rates", request.url.encodedPath)
        assertEquals("""{"samples":[{"measuredAt":"2026-10-20T07:15:00+09:00","bpm":150}]}""", request.body!!.utf8())
    }

    @Test
    fun `전송 실패는 AppError로 돌아온다`() = runTest {
        api.reply("""{"code":"INVALID_STATE"}""", code = 409)

        assertEquals(Outcome.Failure(AppError.Rejected("INVALID_STATE")), RunUploadRemoteImpl(runApi).upload(RUN))
    }

    @Test
    fun `파기 지시를 읽고 확인을 보낸다`() = runTest {
        api.reply(
            """[{"id":1,"kind":"ROUTE","dueAt":"2026-10-27T07:00:00","status":"SCHEDULED"},{"id":2,"kind":"HEART_RATE"},{"id":3,"kind":"UNKNOWN"}]"""
        )
        api.replyEmpty()
        val remote = DisposalRemoteImpl(runApi)

        val orders = remote.pending()
        val confirmed = remote.confirm(2)

        assertEquals(
            Outcome.Success(
                listOf(DisposalOrder(1, DisposalOrder.Kind.ROUTE), DisposalOrder(2, DisposalOrder.Kind.HEART_RATE))
            ),
            orders
        )
        assertEquals(Outcome.Success(Unit), confirmed)
        assertEquals("/api/v1/me/disposals", api.take().url.encodedPath)
        val confirm = api.take()
        assertEquals("POST /api/v1/me/disposals/2/confirm", "${confirm.method} ${confirm.url.encodedPath}")
    }

    private companion object {
        val START: Instant = Instant.parse("2026-10-19T22:00:00Z")
        val RUN =
            CompletedRun(
                recordId = "0f8e6c1a-4d2b-4c55-9d8e-3b6c1f7a2e90",
                mode = RunMode.BATTLE,
                cardKind = CardKind.BATTLE_RUN,
                battleId = 42,
                crewRunPostId = null,
                startedAt = START,
                elapsed = Duration.ofMillis(605_500),
                distance = Distance(2_012),
                route =
                    listOf(
                        RoutePoint(START.plusSeconds(1), GeoPoint(37.501234, 127.034567), SignalState.NORMAL),
                        RoutePoint(START.plusSeconds(2), GeoPoint(37.501301, 127.034601), SignalState.UNSTABLE)
                    ),
                unstable = listOf(UnstableSegment(START.plusSeconds(100), START.plusSeconds(115), Distance(42))),
                heartRates = listOf(HeartRateSample(START.plusSeconds(10), 130)),
                savedAt = START.plusSeconds(606)
            )
    }
}
