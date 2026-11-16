package com.a207.runkk.app.api.crew

import com.a207.runkk.app.api.core.ApiTestServer
import com.a207.runkk.app.api.core.value
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.crew.CrewRunJoin
import com.a207.runkk.app.domain.crew.Lobby
import com.a207.runkk.app.domain.crew.LobbyPhase
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import retrofit2.create

/**
 * 크루런 실시간 반영의 새 REST 계약(설계 05-32 5.1·5.2, TC-11019·11025·11028·11033·11042 앱 부분). 응답 본문은 백엔드 계약 표본
 * (backend/src/test/resources/contract/crew)을 그대로 옮긴 것이다.
 */
class CrewRunRealtimeRepositoryTest {
    private val api = ApiTestServer()
    private val repository = CrewRepositoryImpl(api.retrofit.create(), api.faces)

    @AfterTest
    fun tearDown() = api.close()

    private fun sample(name: String): String =
        requireNotNull(javaClass.getResource("/contract/crew/$name")) { "표본 없음: $name" }.readText()

    @Test
    fun `TC-11019 대기실 다시 읽기는 GET이고 카운트다운 표본(고정 후보·startAt·phase)을 읽는다`() = runTest {
        api.reply(sample("LobbyResponse-countdown.json"))

        val lobby = repository.lobby(101).value()

        val request = api.take()
        assertEquals("GET", request.method)
        assertEquals("/api/v1/crew-runs/101/lobby", request.url.encodedPath)
        assertEquals(LobbyPhase.COUNTDOWN, lobby.phase)
        assertEquals(7L, lobby.crewId)
        assertEquals(19L, lobby.revision)
        assertEquals("101-18", lobby.countdownToken)
        assertEquals(Instant.parse("2026-11-16T10:00:03Z"), lobby.startAt, "startAt은 이미 3초를 더한 절대시각이다")
        assertEquals(listOf(11L), lobby.countdownParticipantIds)
        assertTrue(lobby.isMeCandidate)
        assertFalse(lobby.canStartNow)
        assertFalse(lobby.isMeRunner)
        assertEquals(null, lobby.measurementRecordId)
        assertEquals(Distance(5_000), lobby.distance)
        assertEquals(listOf(true, false, false), lobby.participants.map(Lobby.Participant::isReady))
    }

    @Test
    fun `진행 표본은 RUNNING·isMeRunner·내 측정 기록 ID를 읽는다`() = runTest {
        api.reply(sample("LobbyResponse-running.json"))

        val lobby = repository.lobby(101).value()

        assertEquals(LobbyPhase.RUNNING, lobby.phase)
        assertTrue(lobby.isMeRunner)
        assertEquals("6f1c2a0e-3b7d-4c55-9a8e-2f4b1d0c9e71", lobby.measurementRecordId)
    }

    @Test
    fun `멱등 준비는 PUT 본문 ready, 지금 시작은 POST start다`() = runTest {
        api.reply(sample("LobbyResponse-countdown.json"))
        api.reply(sample("LobbyResponse-countdown.json"))

        repository.setReady(101, ready = false)
        repository.startNow(101)

        val ready = api.take()
        assertEquals("PUT", ready.method)
        assertEquals("/api/v1/crew-runs/101/lobby/ready", ready.url.encodedPath)
        assertEquals("""{"ready":false}""", ready.body!!.utf8())
        val start = api.take()
        assertEquals("POST", start.method)
        assertEquals("/api/v1/crew-runs/101/start", start.url.encodedPath)
    }

    @Test
    fun `TC-11028 중간 참여는 POST join이고 승인 시각·측정 기록 ID·내 줄 끝남을 읽는다`() = runTest {
        api.reply(sample("ParticipationResponse.json"))

        val joined = repository.join(101).value()

        assertEquals("/api/v1/crew-runs/101/join", api.take().url.encodedPath)
        assertEquals(
            CrewRunJoin(
                postId = 101,
                crewId = 7,
                revision = 22,
                joinedAt = Instant.parse("2026-11-16T10:05:12.345Z"),
                measurementRecordId = "0b7e9c55-1d2f-4a6b-8c3e-5f9a7d1e2c40",
                isAlreadyJoined = false,
                isEnded = false
            ),
            joined
        )
    }

    @Test
    fun `TC-11033 크루런 탭 목록은 날짜순·진행 중만 요청하고 커서를 이어 보낸다`() = runTest {
        api.reply("""{"posts":[],"nextCursor":null}""")

        repository.activeCrewRuns(7, cursor = 55)

        val url = api.take().url
        assertEquals("/api/v1/crews/7/posts", url.encodedPath)
        assertEquals("CREW_RUN", url.queryParameter("kind"))
        assertEquals("SCHEDULED_ASC", url.queryParameter("order"))
        assertEquals("true", url.queryParameter("activeOnly"))
        assertEquals("55", url.queryParameter("cursor"))
    }

    @Test
    fun `옛 목록은 order·activeOnly를 보내지 않는다`() = runTest {
        api.reply("""{"posts":[],"nextCursor":null}""")

        repository.posts(7, null)

        val url = api.take().url
        assertEquals(null, url.queryParameter("order"))
        assertEquals(null, url.queryParameter("activeOnly"))
    }
}
