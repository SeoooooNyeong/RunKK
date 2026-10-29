package com.a207.runkk.app.api.record

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.SelfMember
import com.a207.runkk.app.api.core.TokenProvider
import com.a207.runkk.app.api.core.jwtOf
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.CardWeather
import com.a207.runkk.app.domain.record.Dashboard
import com.a207.runkk.app.domain.record.Ranking
import com.a207.runkk.app.domain.record.RecordDetail
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.record.RunCard
import com.a207.runkk.app.domain.record.RunSummary
import com.a207.runkk.app.domain.record.TodayPlan
import com.a207.runkk.app.domain.record.WeeklyGoalProgress
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import retrofit2.create

/** api.record 매퍼(14장 단위(앱 데이터) "api.<기능> 매퍼"): 대시보드·기록 상세·랭킹·오늘 계획·카드 DTO → 도메인. */
class RecordRepositoryImplTest {
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

    @Test
    fun `SELF 기록 목록은 토큰의 본인 회원 ID로 부른다`() = runTest {
        api.reply("""{"runs":[]}""")

        repository.runs(RecordRepository.SELF, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

        assertEquals("/api/v1/members/7/runs", api.take().url.encodedPath)
    }

    @Test
    fun `다른 회원 ID는 그대로 부른다`() = runTest {
        api.reply("""{"runs":[]}""")

        repository.runs(9, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))

        assertEquals("/api/v1/members/9/runs", api.take().url.encodedPath)
    }

    @Test
    fun `대시보드 DTO를 도메인으로 바꾼다`() = runTest {
        api.reply(
            """{"todayMeters":3200,"totalMeters":152300,"landmark":"서울→부산 37%","streakDays":4,
               |"grass":{"2026-10-19":5000,"2026-10-20":3200},"weeklyAchievement":0.6,
               |"crew":{"crewName":"새벽런","streakDays":9,"grass":{"2026-10-20":12000},"landmark":null},
               |"todaySeconds":1100,"todayRuns":2,"grassLevels":{"2026-10-19":3,"2026-10-20":2},
               |"weekly":{"weekStart":"2026-10-19","goalRuns":4,"goalMeters":20000,"actualRuns":2,"actualMeters":12000,
               |"runsPercent":50,"distancePercent":60},"rewardBalance":1250}
            """.trimMargin()
        )

        val dashboard = repository.dashboard(LocalDate.of(2026, 10, 20)).value()

        assertEquals(
            Dashboard(
                today = Distance(3_200),
                total = Distance(152_300),
                landmark = "서울→부산 37%",
                streakDays = 4,
                grass = mapOf(
                    LocalDate.of(2026, 10, 19) to Distance(5_000),
                    LocalDate.of(2026, 10, 20) to Distance(3_200)
                ),
                weeklyAchievement = 0.6,
                crew = Dashboard.CrewArea("새벽런", 9, mapOf(LocalDate.of(2026, 10, 20) to Distance(12_000)), null),
                todayElapsed = Duration.ofSeconds(1_100),
                todayRuns = 2,
                weekly = WeeklyGoalProgress(
                    goalRuns = 4,
                    goalDistance = Distance(20_000),
                    actualRuns = 2,
                    actualDistance = Distance(12_000),
                    runsPercent = 50,
                    distancePercent = 60
                ),
                grassLevels = mapOf(LocalDate.of(2026, 10, 19) to 3, LocalDate.of(2026, 10, 20) to 2),
                rewardBalance = 1_250
            ),
            dashboard
        )
        assertEquals("/api/v1/dashboard?baseDate=2026-10-20", api.take().target)
    }

    @Test
    fun `TC-9743 옛 서버 대시보드는 리워드 잔액 없이 해독된다`() = runTest {
        api.reply("""{"todayMeters":0,"totalMeters":0}""")

        assertEquals(null, repository.dashboard(LocalDate.of(2026, 10, 20)).value().rewardBalance)
    }

    @Test
    fun `기록 상세는 요약·경로·불안정 구간·심박 구간을 바꾼다`() = runTest {
        api.reply(
            """{"summary":{"recordId":"r-1","startedAt":"2026-10-20T07:00:00","distanceMeters":5010,"elapsedSeconds":1620,
               |"averagePaceSecondsPerKm":323,"valid":true,"mode":"BATTLE","cardKind":"BATTLE_RUN"},
               |"route":[{"latitude":37.5,"longitude":127.0},{"latitude":37.501,"longitude":127.001}],
               |"unstableRanges":[{"start":3,"end":7}],"unstableSegments":[{"fromSeconds":3,"toSeconds":7,"distanceMeters":12}],
               |"calories":320,"heartRateZones":{"ZONE_3":600},"splitPaces":[330,318,325],"fastestKmSecondsPerKm":318,
               |"averageHeartRate":152,"maxHeartRate":178,"newRecords":["KM5"],"rewardPoints":30}
            """.trimMargin()
        )

        val detail = repository.run("r-1").value()

        assertEquals(
            RunSummary(
                "r-1",
                Instant.parse("2026-10-19T22:00:00Z"),
                Distance(5_010),
                Duration.ofSeconds(1_620),
                Pace(323),
                true,
                RunMode.BATTLE,
                CardKind.BATTLE_RUN
            ),
            detail.summary
        )
        assertEquals(listOf(GeoPoint(37.5, 127.0), GeoPoint(37.501, 127.001)), detail.route)
        assertEquals(
            listOf(RecordDetail.Unstable(Duration.ofSeconds(3), Duration.ofSeconds(7), Distance(12))),
            detail.unstableSegments
        )
        assertEquals(listOf(Pace(330), Pace(318), Pace(325)), detail.splitPaces)
        assertEquals(Pace(318), detail.fastestKm)
        assertEquals(152, detail.averageHeartRate)
        assertEquals(178, detail.maxHeartRate)
        assertEquals(mapOf("ZONE_3" to Duration.ofMinutes(10)), detail.heartRateZones)
        assertEquals(null, detail.crewContribution)
        assertEquals("/api/v1/runs/r-1", api.take().url.encodedPath)
    }

    @Test
    fun `랭킹은 기간 이름으로 묻고 본인 줄과 격차를 읽는다`() = runTest {
        api.reply(
            """{"entries":[{"rank":1,"name":"바람","distanceMeters":42000}],"mine":{"rank":12,"name":"나","distanceMeters":15000},
               |"gapToNextMeters":800,"aggregatedAt":"2026-10-20T06:00:00"}
            """.trimMargin()
        )

        val ranking = repository.personalRanking(AggregationPeriod.WEEKLY).value()

        assertEquals(
            Ranking(
                listOf(Ranking.Entry(1, "바람", Distance(42_000))),
                Ranking.Entry(12, "나", Distance(15_000)),
                Distance(800),
                Instant.parse("2026-10-19T21:00:00Z"),
                null
            ),
            ranking
        )
        assertEquals("/api/v1/rankings/personal?period=WEEKLY", api.take().target)
    }

    @Test
    fun `집계 전 랭킹은 집계 시각이 없다`() = runTest {
        api.reply("""{"entries":[],"aggregatedAt":null,"notice":null}""")

        val ranking = repository.crewRanking(AggregationPeriod.MONTHLY).value()

        assertEquals(null, ranking.aggregatedAt)
    }

    @Test
    fun `주간 목표는 횟수와 거리를 함께 보낸다`() = runTest {
        api.replyEmpty()

        repository.setWeeklyGoal(Distance(20_000), runs = 4)

        val request = api.take()
        assertEquals("PUT /api/v1/goals/weekly", "${request.method} ${request.url.encodedPath}")
        assertEquals("""{"runs":4,"distanceMeters":20000}""", request.body!!.utf8())
    }

    @Test
    fun `오늘 계획이 없으면 204라도 성공 null이다`() = runTest {
        api.replyEmpty(204)
        api.reply("""{"distanceMeters":5000,"paceSecondsPerKm":360}""")
        api.reply("""{}""")

        assertEquals(Outcome.Success(null), repository.todayPlan())
        assertEquals(Outcome.Success(TodayPlan(Distance(5_000), Pace(360))), repository.todayPlan())
        assertEquals(Outcome.Success(null), repository.todayPlan())
    }

    @Test
    fun `계획 넣기는 날짜 경로와 미터·초 본문으로 보낸다`() = runTest {
        api.replyEmpty()

        repository.putPlan(LocalDate.of(2026, 10, 22), Distance(7_000), Pace(350))

        val request = api.take()
        assertEquals("PUT /api/v1/plans/2026-10-22", "${request.method} ${request.url.encodedPath}")
        assertEquals("""{"distanceMeters":7000,"paceSecondsPerKm":350}""", request.body!!.utf8())
    }

    @Test
    fun `공유 카드 값을 바꾸고 모르는 카드 종류는 자유런으로 읽는다`() = runTest {
        api.reply(
            """{"recordId":"r-2","cardKind":"NEW_KIND","startedAt":"2026-10-20T07:00:00+09:00","distanceMeters":3000,
               |"elapsedSeconds":1000,"route":[],"courseMapShared":false}
            """.trimMargin()
        )

        val card = repository.card("r-2").value()

        assertEquals(
            RunCard(
                "r-2",
                CardKind.FREE_RUN,
                Instant.parse("2026-10-19T22:00:00Z"),
                Distance(3_000),
                Duration.ofSeconds(1_000),
                null,
                emptyList(),
                false
            ),
            card
        )
    }

    @Test
    fun `카드 앞면 값 - 평균 심박·신기록·날씨·닉네임을 읽고 모르는 하늘 상태는 비운다(D-235)`() = runTest {
        api.reply(
            """{"recordId":"r-3","cardKind":"FREE_RUN","startedAt":"2026-10-21T19:12:00+09:00","distanceMeters":5020,
               |"elapsedSeconds":1721,"averagePaceSecondsPerKm":343,"averageHeartRate":152,"newRecords":["최장 거리 신기록"],
               |"route":[],"courseMapShared":false,"temperatureC":14,"sky":"CLEAR","nickname":"달리는곰"}
            """.trimMargin()
        )
        val card = repository.card("r-3").value()
        assertEquals(152, card.averageHeartRate)
        assertEquals(listOf("최장 거리 신기록"), card.newRecords)
        assertEquals(CardWeather(14, CardWeather.Sky.CLEAR), card.weather)
        assertEquals("달리는곰", card.ownerName)

        api.reply(
            """{"recordId":"r-4","cardKind":"FREE_RUN","startedAt":"2026-10-21T19:12:00+09:00","distanceMeters":10,
               |"elapsedSeconds":10,"sky":"HAIL"}
            """.trimMargin()
        )
        assertEquals(CardWeather(null, null), repository.card("r-4").value().weather)
    }

    @Test
    fun `결과 휴식은 서버 pausedSeconds로 읽고 없으면 비운다(D-282)`() = runTest {
        api.reply(
            """{"summary":{"recordId":"r-5","startedAt":"2026-10-21T19:12:00+09:00","distanceMeters":5020,"elapsedSeconds":1668,
               |"valid":true,"mode":"FREE","cardKind":"FREE_RUN"},"pausedSeconds":185}
            """.trimMargin()
        )
        assertEquals(Duration.ofSeconds(185), repository.run("r-5").value().paused)
    }
}
