package com.a207.runkk.app.api.crew

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.domain.crew.CrewRunStatus
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import retrofit2.create

/**
 * 피그마 맞춤 W7(contract-W7 2~4장, contract-W6 지난 크루런, contract-W4 크루 상세 통계): 크루원 누적 거리·가입일·공개 정보,
 * 가입 신청자 보조 정보, 지난 공동 목표·지난 크루런 해독과 요청 경로.
 */
class CrewW7RepositoryTest {
    private val api = ApiTestServer()
    private val repository = CrewRepositoryImpl(api.retrofit.create(), api.faces)

    @AfterTest
    fun tearDown() = api.close()

    private fun request(): String = api.take().let { "${it.method} ${it.url.encodedPath}?${it.url.encodedQuery}" }

    @Test
    fun `크루원 목록의 누적 거리·가입일·레벨·나이대·성별·본인 공개 설정을 읽는다`() = runTest {
        api.reply(
            """{"members":[
               |{"memberId":12,"displayName":"달리는곰","role":"LEADER","crewIntro":"한강","tier":"GOLD","me":true,
               | "accumulatedMeters":512390,"joinedAt":"2026-09-01T07:30:00+09:00","level":23,"ageBand":"20대",
               | "gender":"FEMALE","ageBandVisibility":"PUBLIC","genderVisibility":"PRIVATE"},
               |{"memberId":31,"displayName":"새벽러너","role":"MEMBER","me":false,"accumulatedMeters":0,
               | "joinedAt":"2026-11-02T21:10:05+09:00","level":4}
               |]}
            """.trimMargin()
        )

        val (me, other) = repository.members(5).value().members

        assertEquals(Distance(512_390), me.accumulated)
        assertEquals(Instant.parse("2026-08-31T22:30:00Z"), me.joinedAt)
        assertEquals(23, me.level)
        assertEquals("20대", me.ageBand)
        assertEquals(Gender.FEMALE, me.gender)
        assertEquals(true, me.isAgePublic)
        assertEquals(false, me.isGenderPublic)
        assertNull(other.ageBand)
        assertNull(other.gender)
        assertNull(other.isAgePublic)
        assertEquals(Distance(0), other.accumulated)
    }

    @Test
    fun `가입 신청자의 티어·레벨·나이대·성별을 읽는다`() = runTest {
        api.reply(
            """{"requests":[{"requestId":88,"memberId":45,"displayName":"언덕왕","tier":"GOLD",
               |"requestedAt":"2026-11-08T19:02:11+09:00","level":23,"ageBand":"20대","gender":"MALE"}]}
            """.trimMargin()
        )

        val request = repository.pendingRequests(5).value().requests.single()

        assertEquals("GOLD", request.tier)
        assertEquals(23, request.level)
        assertEquals("20대", request.ageBand)
        assertEquals(Gender.MALE, request.gender)
    }

    @Test
    fun `크루 상세의 개설일·누적 거리·누적 크루런 횟수를 읽고 없으면 비운다`() = runTest {
        api.reply(
            """{"crewId":5,"name":"새벽러너스","visibility":"PUBLIC","joinPolicy":"FREE","leaderId":1,"headcount":7,
               |"createdAt":"2026-10-01T09:00:00+09:00","totalDistanceMeters":1284670,"crewRunCount":5}
            """.trimMargin()
        )
        api.reply("""{"crewId":5,"name":"옛서버","visibility":"PUBLIC","joinPolicy":"FREE","leaderId":1,"headcount":1}""")

        val detail = repository.detail(5).value()
        val old = repository.detail(5).value()

        assertEquals(LocalDate.of(2026, 10, 1), detail.createdAt)
        assertEquals(Distance(1_284_670), detail.totalDistance)
        assertEquals(5, detail.crewRunCount)
        assertNull(old.createdAt)
        assertNull(old.totalDistance)
        assertNull(old.crewRunCount)
    }

    @Test
    fun `지난 공동 목표는 before 커서로 이어 받고 100을 넘는 진행률을 그대로 둔다`() = runTest {
        api.reply(
            """{"goals":[
               |{"weekStart":"2026-10-12","month":10,"weekOfMonth":3,"goalMeters":80000,"totalMeters":72000,
               | "achieved":false,"progressPercent":90,"participantCount":6},
               |{"weekStart":"2026-10-05","month":10,"weekOfMonth":2,"goalMeters":50000,"totalMeters":61500,
               | "achieved":true,"progressPercent":123,"participantCount":7}],"nextBefore":"2026-10-05"}
            """.trimMargin()
        )
        api.reply("""{"goals":[],"nextBefore":null}""")

        val first = repository.pastGoals(5).value()
        val next = repository.pastGoals(5, first.nextBefore).value()

        assertEquals(LocalDate.of(2026, 10, 5), first.nextBefore)
        assertEquals(123, first.goals[1].progressPercent)
        assertEquals(true, first.goals[1].isAchieved)
        assertEquals(3, first.goals[0].weekOfMonth)
        assertEquals(Distance(72_000), first.goals[0].total)
        assertNull(next.nextBefore)
        assertEquals("GET /api/v1/crews/5/goals/history?size=20", request())
        assertEquals("GET /api/v1/crews/5/goals/history?before=2026-10-05&size=20", request())
    }

    @Test
    fun `지난 크루런 한 달을 읽고 날짜를 주면 그날로 묻는다`() = runTest {
        api.reply(
            """{"month":"2026-10","dates":["2026-10-13","2026-10-20"],"runs":[
               |{"postId":40,"title":"화요일 인터벌","scheduledAt":"2026-10-20T19:00:00+09:00","distanceMeters":5000,
               | "status":"FINISHED","goingCount":6,"runnerCount":6},
               |{"postId":35,"title":"화요일 인터벌","scheduledAt":"2026-10-13T19:00:00+09:00","distanceMeters":null,
               | "status":"CANCELLED","goingCount":3,"runnerCount":0}],"earliestMonth":"2026-09"}
            """.trimMargin()
        )
        api.reply("""{"month":"2026-10","dates":["2026-10-13"],"runs":[],"earliestMonth":null}""")

        val month = repository.pastCrewRuns(5, YearMonth.of(2026, 10)).value()
        repository.pastCrewRuns(5, YearMonth.of(2026, 10), LocalDate.of(2026, 10, 20)).value()

        assertEquals(setOf(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 20)), month.days)
        assertEquals(YearMonth.of(2026, 9), month.earliestMonth)
        assertEquals(CrewRunStatus.CANCELLED, month.runs[1].status)
        assertNull(month.runs[1].distance)
        assertEquals(Distance(5_000), month.runs[0].distance)
        assertEquals("GET /api/v1/crews/5/crew-runs/past?month=2026-10", request())
        assertEquals("GET /api/v1/crews/5/crew-runs/past?month=2026-10&date=2026-10-20", request())
    }
}
