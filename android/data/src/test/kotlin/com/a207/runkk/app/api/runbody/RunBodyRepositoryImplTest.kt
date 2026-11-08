package com.a207.runkk.app.api.runbody

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.runbody.RunBodyAxis
import com.a207.runkk.app.domain.runbody.RunBodyMonth
import com.a207.runkk.app.domain.runbody.RunBodyRepository
import com.a207.runkk.app.domain.runbody.RunBodyResult
import com.a207.runkk.app.domain.runbody.RunBodyRunScore
import com.a207.runkk.app.domain.runbody.RunBodyStatus
import com.a207.runkk.app.domain.runbody.RunBodyTrend
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import retrofit2.create

/** api.runbody(D-453): 계약 runkk-work/runbody/contract.md v1.2의 예시 본문을 그대로 읽는다. */
class RunBodyRepositoryImplTest {
    private val api = ApiTestServer()
    private val repository = RunBodyRepositoryImpl(api.retrofit.create())

    @AfterTest
    fun tearDown() = api.close()

    @Test
    fun `완료된 세션은 ai 결과 본문을 축·구간·직전·피드백·코칭으로 읽는다`() = runTest {
        api.reply(DONE_BODY)

        val session = repository.session("rec-1").value()

        assertEquals("GET /api/v1/runbody/sessions/rec-1", api.take().let { "${it.method} ${it.url.encodedPath}" })
        assertEquals(RunBodyStatus.DONE, session.status)
        val result = session.result!!
        assertEquals(Distance(5_200), result.summary.distance)
        assertEquals(Duration.ofMillis(1_810_000), result.summary.active)
        assertEquals(Pace(348), result.summary.pace)
        assertEquals(Instant.parse("2026-11-01T22:10:00Z"), result.summary.startedAt)
        assertEquals(78, result.axes.getValue(RunBodyAxis.INTENSITY).score)
        assertNull(result.axes.getValue(RunBodyAxis.FORM).score)
        assertEquals(RunBodyResult.AxisScore.Level.NONE, result.axes.getValue(RunBodyAxis.FORM).level)
        assertEquals(
            listOf(RunBodyAxis.INTENSITY, RunBodyAxis.ENDURANCE, RunBodyAxis.STABILITY, RunBodyAxis.FITNESS),
            result.scoredAxes
        )
        assertEquals(RunBodyResult.Lap(0.2, Pace(340), 156, isPartial = true), result.laps.last())
        assertEquals(RunBodyResult.Previous(Distance(5_000), Pace(362)), result.previous)
        assertEquals(listOf(RunBodyResult.Coaching.POSITIVE_SPLIT), result.coaching)
        assertEquals(RunBodyResult.Split(Pace(344), Pace(352)), result.split)
        assertEquals("후반에 조금 처졌어요", result.feedback.headline)
    }

    @Test
    fun `대기·분석 중·실패는 결과 없이 상태만, 모르는 상태는 해독 실패다`() = runTest {
        api.reply("""{"recordId":"rec-1","status":"PENDING","result":null}""")
        api.reply("""{"recordId":"rec-1","status":"ANALYZING"}""")
        api.reply("""{"recordId":"rec-1","status":"FAILED","result":null}""")
        api.reply("""{"recordId":"rec-1","status":"QUEUED"}""")

        assertEquals(RunBodyStatus.PENDING, repository.session("rec-1").value().status)
        assertEquals(RunBodyStatus.ANALYZING, repository.session("rec-1").value().status)
        val failed = repository.session("rec-1").value()
        assertEquals(RunBodyStatus.FAILED, failed.status)
        assertNull(failed.result)
        assertNull(failed.failureReason)
        assertEquals(Outcome.Failure(AppError.Server(0)), repository.session("rec-1"))
    }

    @Test
    fun `출생연도가 없어 실패한 분석은 사유를 읽는다(MR !91 e2e 1)`() = runTest {
        api.reply("""{"recordId":"rec-1","status":"FAILED","result":null,"failureReason":"BIRTH_YEAR_MISSING"}""")

        val failed = repository.session("rec-1").value()

        assertEquals(RunBodyStatus.FAILED, failed.status)
        assertEquals(RunBodyRepository.BIRTH_YEAR_MISSING, failed.failureReason)
    }

    @Test
    fun `작업 없음·기능 닫힘은 사유 코드로, ai 읽기 실패는 서버 오류로 돌려준다`() = runTest {
        api.reply("""{"code":"RUNBODY_NOT_FOUND","message":"분석 작업 없음"}""", code = 404)
        api.reply("""{"code":"FEATURE_CLOSED","message":"닫힘"}""", code = 403)
        api.reply("""{"code":"RUNBODY_UNAVAILABLE","message":"ai 응답 없음"}""", code = 503)

        assertEquals(Outcome.Failure(AppError.Rejected(RunBodyRepository.NOT_FOUND)), repository.session("rec-1"))
        assertEquals(
            Outcome.Failure(AppError.Rejected(RunBodyRepository.FEATURE_CLOSED)),
            repository.month(YearMonth.of(2026, 10))
        )
        assertEquals(Outcome.Failure(AppError.Server(503)), repository.session("rec-1"))
    }

    @Test
    fun `월간 집계는 yyyy-MM 경로로 읽고 점수가 없는 축은 뺀다`() = runTest {
        api.reply(MONTH_BODY)

        val month = repository.month(YearMonth.of(2026, 10)).value()

        assertEquals("/api/v1/runbody/months/2026-10", api.take().url.encodedPath)
        assertEquals(12, month.sessionCount)
        assertEquals(
            mapOf(
                RunBodyAxis.INTENSITY to 76,
                RunBodyAxis.ENDURANCE to 82,
                RunBodyAxis.STABILITY to 70,
                RunBodyAxis.FITNESS to 78
            ),
            month.averages
        )
        assertEquals(Duration.ofSeconds(292), month.bests.fastestKm)
        assertEquals(Distance(21_300), month.bests.longest)
        assertNull(month.bests.recoveryHeartRate1m)
        assertEquals(RunBodyMonth.Week(LocalDate.of(2026, 10, 6), Distance(18_000)), month.weeks[1])
        assertEquals(3, month.nightRuns)
        assertEquals(RunBodyMonth.Load.Level.NORMAL, month.load.level)
        assertEquals(Distance(16_200), month.load.chronicWeekly)
        assertEquals(Pace(348), month.baseline.pace)
    }

    @Test
    fun `준비 중인 부하는 켜지는 날을, 빈 달은 0회를 읽는다`() = runTest {
        api.reply(
            """
            {"userId":"42","yearMonth":"2026-11","asOf":"2026-11-07T00:00:00Z","engineVersion":"0.2","sessionCount":0,
             "distanceMeters":0,"activeMs":0,"paceSecondsPerKm":null,"avgHeartRate":null,
             "axes":{"intensity":{"average":null,"count":0,"ref":70}},
             "bests":{"fastestKmSeconds":null,"fastest2kmSeconds":null,"longestRunMeters":null,"bestRecoveryHeartRate1m":null},
             "weeks":[],"timeOfDay":{"night":0,"day":0},
             "load":{"available":false,"ratio":null,"level":null,"acuteMeters":5200,"chronicWeeklyMeters":1300,"availableFrom":"2026-11-21"},
             "baseline":{"available":false,"sessionCount":2,"needs":1,"paceSecondsPerKm":null,"avgHeartRate":null,"cadenceMedian":null,"recoveryHeartRate1m":null},
             "avatarInputs":{"distance14dMeters":5200,"sessions14d":2}}
            """.trimIndent()
        )

        val month = repository.month(YearMonth.of(2026, 11)).value()

        assertEquals(0, month.sessionCount)
        assertEquals(emptyMap(), month.averages)
        assertEquals(LocalDate.of(2026, 11, 21), month.load.availableFrom)
        assertEquals(java.time.Instant.parse("2026-11-07T00:00:00Z"), month.asOf, "그 달을 셈한 기준 시점(MR !91 e2e 6)")
        assertNull(month.load.level)
        assertEquals(1, month.baseline.needs)
    }

    @Test
    fun `러닝 점수 목록은 size 100으로 읽고 모르는 상태 줄은 버리며 범위 밖 점수는 없는 것으로 본다(계약 7절)`() = runTest {
        api.reply(
            """
            {"items":[
              {"recordId":"r3","status":"DONE","score":78,"scoreChange":6},
              {"recordId":"r2","status":"ANALYZING","score":null,"scoreChange":null},
              {"recordId":"r1","status":"DONE","score":72,"scoreChange":null},
              {"recordId":"rx","status":"QUEUED","score":50},
              {"recordId":"ry","status":"DONE","score":140,"scoreChange":3}],
             "nextCursor":"ry"}
            """.trimIndent()
        )

        val scores = repository.scores().value()

        val request = api.take()
        assertEquals("GET /api/v1/runbody/sessions", "${request.method} ${request.url.encodedPath}")
        assertEquals("100", request.url.queryParameter("size"))
        assertEquals(
            listOf(
                RunBodyRunScore("r3", RunBodyStatus.DONE, 78, 6),
                RunBodyRunScore("r2", RunBodyStatus.ANALYZING, null, null),
                RunBodyRunScore("r1", RunBodyStatus.DONE, 72, null),
                RunBodyRunScore("ry", RunBodyStatus.DONE, null, null)
            ),
            scores
        )
    }

    @Test
    fun `최근 10회 변화는 회차·직전과 최근·같은 심박 페이스·러닝 유형을 읽는다(계약 8절)`() = runTest {
        api.reply(
            """
            {"validRunCount":23,"round":2,"needs":0,
             "recent":{"fromOrdinal":11,"toOrdinal":20,"paceSecondsPerKm":348,"runsPerWeek":2.8,"runsPerMonth":11.1,"averageHeartRate":150},
             "previous":{"fromOrdinal":1,"toOrdinal":10,"paceSecondsPerKm":362,"runsPerWeek":2.1,"runsPerMonth":9.0,"averageHeartRate":149},
             "sameHeartRatePaceChange":12,"runimalType":"COMMUTER_TURTLE"}
            """.trimIndent()
        )
        api.reply("""{"validRunCount":4,"round":0,"needs":6,"recent":null,"previous":null}""")

        val trend = repository.trend().value()

        assertEquals("GET /api/v1/runbody/trend", api.take().let { "${it.method} ${it.url.encodedPath}" })
        assertEquals(2, trend.round)
        assertEquals(RunBodyTrend.Window(11, 20, Pace(348), 2.8, 11.1, 150), trend.recent)
        assertEquals(Pace(362), trend.previous?.pace)
        assertEquals(14, trend.paceChange)
        assertEquals(12, trend.sameHeartRatePaceChange)
        assertEquals("COMMUTER_TURTLE", trend.runimalType)

        val early = repository.trend().value()
        assertEquals(RunBodyTrend(4, 0, 6, null, null, null, null), early)
        assertNull(early.paceChange)
    }

    private companion object {
        val DONE_BODY = """
            {"recordId":"rec-1","status":"DONE","result":{
              "sessionId":"rec-1","userId":"42","schemaVersion":2,"engineVersion":"0.2",
              "summary":{"distanceMeters":5200,"activeMs":1810000,"paceSecondsPerKm":348,"avgHeartRate":149,"maxHeartRate":168,
                "cadenceMedian":null,"recoveryHeartRate1m":-18,"startedAt":"2026-11-01T22:10:00Z","source":"watch"},
              "axes":{
                "intensity":{"score":78,"ref":70,"value":32.5,"label":"적정","level":"good"},
                "endurance":{"score":84,"ref":70,"value":-2.1,"label":"유지","level":"good"},
                "stability":{"score":72,"ref":70,"value":8.4,"label":"보통","level":"mid"},
                "form":{"score":null,"ref":70,"value":null,"label":"케이던스 없음","level":"none"},
                "fitness":{"score":80,"ref":70,"value":-18,"label":"보통","level":"mid"}},
              "metrics":{"intensity":{"z4Pct":12.0}},
              "coaching":{"flags":["positive_split"],"detail":{"split":{"type":"positive","firstHalfPaceS":344,"secondHalfPaceS":352,"ratio":1.023}}},
              "laps":[{"km":1.0,"paceSeconds":342,"avgHeartRate":148,"partial":false},
                      {"km":2.0,"paceSeconds":346,"avgHeartRate":150,"partial":false},
                      {"km":0.2,"paceSeconds":340,"avgHeartRate":156,"partial":true}],
              "prev":{"sessionId":"rec-0","distanceMeters":5000,"paceSecondsPerKm":362,"recoveryHeartRate1m":-15},
              "feedback":{"headline":"후반에 조금 처졌어요","good":"페이스를 일정하게 지켰어요","fix":"후반 페이스가 느려졌어요",
                "next":"첫 1km를 천천히 출발해 보세요","moreFixes":[],"ruleId":"positive_split","goodRuleId":"steady_pace",
                "source":"template","modelVersion":null},
              "qualityFlags":["no_cadence"]}}
        """.trimIndent()

        val MONTH_BODY = """
            {"userId":"42","yearMonth":"2026-10","asOf":"2026-10-31T14:59:59Z","engineVersion":"0.2","sessionCount":12,
             "distanceMeters":61200,"activeMs":21540000,"paceSecondsPerKm":352,"avgHeartRate":149,
             "axes":{"intensity":{"average":76,"count":12,"ref":70},"endurance":{"average":82,"count":12,"ref":70},
                     "stability":{"average":70,"count":12,"ref":70},"form":{"average":null,"count":0,"ref":70},
                     "fitness":{"average":78,"count":10,"ref":70}},
             "bests":{"fastestKmSeconds":{"value":292,"sessionId":"a","runDate":"2026-10-21"},
                      "fastest2kmSeconds":{"value":605,"sessionId":"a","runDate":"2026-10-21"},
                      "longestRunMeters":{"value":21300,"sessionId":"b","runDate":"2026-10-12"},
                      "bestRecoveryHeartRate1m":null},
             "weeks":[{"weekStart":"2026-09-29","distanceMeters":12400,"sessionCount":2},
                      {"weekStart":"2026-10-06","distanceMeters":18000,"sessionCount":4}],
             "timeOfDay":{"night":3,"day":9},
             "load":{"available":true,"ratio":1.14,"level":"normal","acuteMeters":18400,"chronicWeeklyMeters":16200,"availableFrom":null},
             "baseline":{"available":true,"sessionCount":20,"needs":0,"paceSecondsPerKm":348,"avgHeartRate":149,
                         "cadenceMedian":null,"recoveryHeartRate1m":-16},
             "avatarInputs":{"distance14dMeters":30200,"sessions14d":6}}
        """.trimIndent()
    }
}
