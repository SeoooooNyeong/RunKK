package com.a207.runkk.app.api.crew

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewForm
import com.a207.runkk.app.domain.crew.CrewPreview
import com.a207.runkk.app.domain.crew.CrewVisibility
import com.a207.runkk.app.domain.crew.JoinPolicy
import com.a207.runkk.app.domain.crew.RecentCrewRun
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import retrofit2.create

/**
 * 피그마 맞춤 W4(S15P31A207-295, contract-W4.md): 크루 상세 통계·최근 크루런, 검색 전체 수, 24시간 가입 제한 시각, 초대 코드 미리보기,
 * 크루 로고 올리기·읽기. 응답 본문은 서버 계약 표본(backend/src/test/resources/contract/crew 폴더의 JSON)과 같다.
 */
class CrewFindW4Test {
    private val api = ApiTestServer()
    private val repository = CrewRepositoryImpl(api.retrofit.create(), api.faces)

    @AfterTest
    fun tearDown() = api.close()

    @Test
    fun `크루 상세 표본(크루 밖, 24시간 제한)을 개설일·누적 거리·크루런·최근 크루런·가입 가능 시각으로 읽는다`() = runTest {
        api.reply(
            """{"crewId":12,"name":"새벽러너스","intro":"새벽에 함께 달려요","tags":["WEEKDAY_DAWN"],"visibility":"PUBLIC",
               |"joinPolicy":"APPROVAL","logoUrl":"/api/v1/crew-logos/7","leaderId":3,"leaderName":"달리는곰","headcount":7,
               |"weeklyDistanceMeters":12300,"lastWeekDistanceMeters":64270,"myRole":null,"inviteCode":null,
               |"inviteCodeExpiresAt":null,"createdAt":"2026-10-01T09:00:00+09:00","totalDistanceMeters":1284670,
               |"crewRunCount":5,"recentCrewRuns":[
               |{"postId":88,"scheduledAt":"2026-10-21T19:30:00+09:00","participantCount":4,"totalDistanceMeters":20150},
               |{"postId":80,"scheduledAt":"2026-10-18T07:00:00+09:00","participantCount":3,"totalDistanceMeters":15000}],
               |"rejoinAvailableAt":"2026-11-10T08:15:00+09:00"}
            """.trimMargin()
        )

        val detail = repository.detail(12).value()

        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), detail.createdAt)
        assertEquals(Distance(1_284_670), detail.totalDistance)
        assertEquals(5, detail.crewRunCount)
        assertEquals(Distance(64_270), detail.lastWeekDistance)
        assertEquals(
            listOf(
                RecentCrewRun(88, Instant.parse("2026-10-21T10:30:00Z"), 4, Distance(20_150)),
                RecentCrewRun(80, Instant.parse("2026-10-17T22:00:00Z"), 3, Distance(15_000))
            ),
            detail.recentCrewRuns
        )
        assertEquals(Instant.parse("2026-11-09T23:15:00Z"), detail.joinableAt)
        assertEquals("/api/v1/crew-logos/7", detail.logoUrl)
    }

    @Test
    fun `옛 상세 응답(새 항목 없음)도 기본값으로 읽는다`() = runTest {
        api.reply(
            """{"crewId":1,"name":"새벽런","visibility":"PUBLIC","joinPolicy":"FREE","leaderId":3,"headcount":2}"""
        )

        val detail = repository.detail(1).value()

        assertEquals(null, detail.createdAt)
        assertEquals(0, detail.crewRunCount)
        assertEquals(emptyList(), detail.recentCrewRuns)
        assertEquals(null, detail.joinableAt)
    }

    @Test
    fun `크루 검색은 전체 수와 로고 주소를 읽는다`() = runTest {
        api.reply(
            """{"crews":[{"crewId":12,"name":"새벽러너스","intro":"새벽에 함께 달려요","tags":["WEEKDAY_DAWN"],"logoUrl":null,
               |"leaderName":"달리는곰","headcount":7,"weeklyDistanceMeters":12300,"joinPolicy":"APPROVAL"}],
               |"nextCursor":12,"totalCount":3}
            """.trimMargin()
        )

        val list = repository.search("새벽", emptySet()).value()

        assertEquals(3L, list.totalCount)
        assertEquals(12L, list.nextCursor)
        assertEquals(null, list.crews.single().logoUrl)
    }

    @Test
    fun `내 크루 표본(소속 없음, 24시간 제한)은 가입 가능 시각을 준다`() = runTest {
        api.reply("""{"crew":null,"pendingRequests":[],"rejoinAvailableAt":"2026-11-10T08:15:00+09:00"}""")

        val mine = repository.myCrew().value()

        assertEquals(null, mine.crew)
        assertEquals(Instant.parse("2026-11-09T23:15:00Z"), mine.joinableAt)
    }

    @Test
    fun `초대 코드 미리보기는 코드를 본문에 실어 보내고 크루 요약을 돌려준다`() = runTest {
        api.reply(
            """{"crewId":12,"name":"새벽러너스","intro":"새벽에 함께 달려요","tags":["WEEKDAY_DAWN"],"logoUrl":null,
               |"visibility":"PRIVATE","joinPolicy":"APPROVAL","headcount":7,"createdAt":"2026-10-01T09:00:00+09:00"}
            """.trimMargin()
        )

        val preview = repository.previewByInviteCode("AB12CD").value()

        assertEquals(
            CrewPreview(
                12,
                "새벽러너스",
                "새벽에 함께 달려요",
                setOf("WEEKDAY_DAWN"),
                null,
                CrewVisibility.PRIVATE,
                JoinPolicy.APPROVAL,
                7,
                Instant.parse("2026-10-01T00:00:00Z")
            ),
            preview
        )
        val request = api.take()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/crews/invite-preview", request.url.encodedPath)
        assertEquals("""{"inviteCode":"AB12CD"}""", request.body!!.utf8())
    }

    @Test
    fun `틀린 초대 코드는 INVALID_CODE 거부로 온다`() = runTest {
        api.reply("""{"code":"INVALID_CODE","message":"크루 없음 또는 초대 코드 오류·만료"}""", code = 409)

        assertEquals(Outcome.Failure(AppError.Rejected("INVALID_CODE")), repository.previewByInviteCode("ZZZZZZ"))
    }

    @Test
    fun `로고는 multipart 파트 logo로 올리고 로고 주소를 받는다`() = runTest {
        api.reply("""{"logoId":7,"logoUrl":"/api/v1/crew-logos/7"}""", code = 201)

        val url = repository.uploadLogo(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())).value()

        assertEquals("/api/v1/crew-logos/7", url)
        val request = api.take()
        assertEquals("/api/v1/crew-logos", request.url.encodedPath)
        val body = request.body!!.utf8()
        assertTrue("name=\"logo\"" in body, body)
        assertTrue("Content-Type: image/jpeg" in body, body)
    }

    @Test
    fun `로고 바이트는 이 서버의 로고 경로만 읽고 다른 주소는 요청하지 않는다`() = runTest {
        api.reply("jpeg")

        assertEquals("jpeg", String(repository.logo("/api/v1/crew-logos/7").value()))
        assertEquals("/api/v1/crew-logos/7", api.take().url.encodedPath)
        assertEquals(
            Outcome.Failure(AppError.Rejected("NOT_CREW_LOGO")),
            repository.logo("https://cdn.example.com/a.jpg")
        )
        // 같은 로고는 다시 받지 않는다(메모리에 둔다)
        assertEquals("jpeg", String(repository.logo("/api/v1/crew-logos/7").value()))
    }

    @Test
    fun `크루 만들기는 로고 주소를 함께 싣는다`() = runTest {
        api.reply("""{"id":12,"inviteCode":"AB12CD","inviteCodeExpiresAt":"2026-11-16T09:00:00+09:00"}""", code = 201)

        repository.create(
            CrewForm(
                "새벽러너스",
                null,
                setOf("WEEKDAY_DAWN"),
                CrewVisibility.PUBLIC,
                JoinPolicy.FREE,
                "/api/v1/crew-logos/7"
            )
        ).value()

        assertTrue("\"logoUrl\":\"/api/v1/crew-logos/7\"" in api.take().body!!.utf8())
    }
}
