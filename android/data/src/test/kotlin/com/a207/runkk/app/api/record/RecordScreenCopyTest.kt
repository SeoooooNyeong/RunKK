package com.a207.runkk.app.api.record

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.ScreenCopyStore
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.api.core.TokenProvider
import com.a207.runkk.app.api.core.jwtOf
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.Ranking
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.sync.PreloadStage
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import retrofit2.create

/**
 * 랭킹·기록 화면 사본과 시작 미리 받기 출처(S15P31A207-345 2차): 받은 응답은 내 계정 칸(랭킹은 내 순위를 뺀 공용 칸에도)에 남고 지우지
 * 않는다. 다른 계정은 앞 계정의 내 순위·기록을 보지 못하고 공용 랭킹 목록만 보며, 앞 계정으로 돌아오면 그 칸이 바로 보인다. 로그아웃해도
 * 남는다. 출처는 지금 부를 값의 사본이 있으면 받지 않는다.
 */
class RecordScreenCopyTest {
    private val api = ApiTestServer()
    private val dir: File = kotlin.io.path.createTempDirectory("screen-copy").toFile()

    @Volatile
    private var member: String? = "7"
    private val tokens = object : TokenProvider {
        override fun accessToken(): String? = member?.let(::jwtOf)

        override fun refreshToken(): String? = null

        override fun update(access: String, refresh: String) = Unit

        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
    }
    private val self = SelfMember(tokens)
    private val copies = ScreenCopyStore(dir, api.json)
    private val repository = RecordRepositoryImpl(api.retrofit.create(), self, api.faces, copies)
    private val clock = Clock.fixed(Instant.parse("2026-11-19T03:00:00Z"), ZoneId.of("Asia/Seoul"))
    private val source = RecordPreloadSource(repository, copies, self, clock, ZoneId.of("Asia/Seoul"))

    @AfterTest
    fun tearDown() {
        api.close()
        dir.deleteRecursively()
    }

    @Test
    fun `랭킹 사본은 계정 칸에 내 순위와 함께, 공용 칸에 내 순위 없이 남고 계정을 오가도 지우지 않는다`() = runTest {
        api.reply(RANKING)
        repository.personalRanking(AggregationPeriod.WEEKLY).value()

        val mine = repository.cachedPersonalRanking(AggregationPeriod.WEEKLY)
        assertEquals(Ranking.Entry(12, "나", Distance(15_000)), mine?.mine)

        member = null
        assertNull(repository.cachedPersonalRanking(AggregationPeriod.WEEKLY), "로그아웃이면 내주지 않는다")

        member = "8"
        val other = repository.cachedPersonalRanking(AggregationPeriod.WEEKLY)
        assertEquals(listOf("바람"), other?.entries?.map { it.name }, "공용 랭킹 목록은 같이 쓴다")
        assertNull(other?.mine, "다른 계정에 앞 계정 순위가 보이지 않는다")
        assertNull(other?.gapToNext)
        assertNull(repository.cachedPersonalRanking(AggregationPeriod.MONTHLY), "부르지 않은 기간은 없다")

        member = "7"
        assertEquals(
            Ranking.Entry(12, "나", Distance(15_000)),
            repository.cachedPersonalRanking(AggregationPeriod.WEEKLY)?.mine
        )
    }

    @Test
    fun `기록 사본은 계정 칸에만 남아 다른 계정에 보이지 않고 돌아오면 바로 보인다`() = runTest {
        val today = LocalDate.of(2026, 11, 19)
        api.reply(DASHBOARD)
        api.reply("""{"runs":[]}""")
        repository.dashboard(today).value()
        repository.runs(RecordRepository.SELF, today.withDayOfMonth(1), today.withDayOfMonth(30)).value()

        assertTrue(repository.cachedDashboard(today) != null)
        assertNull(repository.cachedDashboard(today.minusDays(1)), "다른 날짜 사본은 쓰지 않는다")
        assertEquals(0, repository.cachedRuns(today.withDayOfMonth(1), today.withDayOfMonth(30))?.runs?.size)

        member = "8"
        assertNull(repository.cachedDashboard(today), "다른 계정에 앞 계정 기록이 보이지 않는다")
        assertNull(repository.cachedRuns(today.withDayOfMonth(1), today.withDayOfMonth(30)))

        member = "7"
        assertTrue(repository.cachedDashboard(today) != null, "돌아오면 앞 계정 칸이 그대로 있다")
    }

    @Test
    fun `미리 받기 출처는 랭킹과 이번 달 기록을 받고 받아 둔 것은 다시 받지 않는다`() = runTest {
        val assets = source.memberAssets().value()
        assertEquals(listOf(PreloadStage.RANKING, PreloadStage.OTHERS), assets.map { it.stage })
        val (ranking, records) = assets
        assertFalse(source.isCached(ranking))
        assertFalse(source.isCached(records))
        assertFalse(records.isOptional, "사본이 하나도 없으면 꼭 받는다")

        api.reply(RANKING)
        var files = 0
        assertEquals(Outcome.Success(Unit), source.fetch(ranking) { files++ })
        assertEquals(ranking.files, files, "머리가 없는 랭킹은 응답을 받자마자 머리 칸까지 다 채운다")
        assertEquals("/api/v1/rankings/personal?period=WEEKLY", api.take().target)

        api.reply(DASHBOARD)
        api.reply("""{"runs":[]}""")
        files = 0
        assertEquals(Outcome.Success(Unit), source.fetch(records) { files++ })
        assertEquals(2, files)
        assertEquals("/api/v1/dashboard?baseDate=2026-11-19", api.take().target)
        assertEquals("/api/v1/members/7/runs?from=2026-11-01&to=2026-11-30", api.take().target)

        assertTrue(source.isCached(ranking), "받아 둔 것은 적중")
        assertTrue(source.isCached(records))
        member = "8"
        assertFalse(source.isCached(records), "다른 계정 칸에는 없다")
    }

    @Test
    fun `랭킹 칸은 응답을 받자마자 쓰지 않는 머리 칸과 내 머리 칸을 채운다(내 머리는 내 아바타 받기가 받는다)`() = runTest {
        val ranking = source.memberAssets().value().first()
        api.reply(RANKING_WITH_MY_HEAD)
        var files = 0
        val result = source.fetch(ranking) { files++ }

        assertEquals(Outcome.Success(Unit), result, "내 머리 받기를 기다리지 않는다")
        assertEquals(ranking.files, files, "응답 1 + 머리 10칸을 다 채운다")
        assertEquals(1, api.server.requestCount, "내 머리(head:7:…)는 랭킹 칸이 받지 않는다")
    }

    private companion object {
        const val RANKING_WITH_MY_HEAD =
            """{"entries":[{"rank":1,"name":"나","distanceMeters":42000,"look":{"faceKey":"head:7:job-1"}},""" +
                """{"rank":2,"name":"바람","distanceMeters":30000}],"mine":{"rank":1,"name":"나","distanceMeters":42000}}"""
        const val RANKING =
            """{"entries":[{"rank":1,"name":"바람","distanceMeters":42000}],"mine":{"rank":12,"name":"나","distanceMeters":15000},"gapToNextMeters":800}"""
        const val DASHBOARD = """{"todayMeters":1000,"totalMeters":5000}"""
    }
}
