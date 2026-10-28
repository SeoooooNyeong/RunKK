package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.testing.T0
import kotlin.test.Test
import kotlin.test.assertEquals

/** 모집글 대기실 단추 규칙(FR-133·FR-147, 서버 CrewRunPost.enterLobby: 30분 전 ≤ now < 모집 일시). */
class LobbyAvailabilityTest {
    private val meetAt = T0.plusSeconds(3 * 3_600)
    private val voting = PostList.CrewRunInfo(
        distance = Distance(5_000),
        scheduledAt = meetAt,
        voteDeadline = null,
        status = CrewRunStatus.VOTING,
        goingCount = 2,
        myAnswer = null,
        finisherCount = 0,
        raceContribution = null
    )

    private fun at(minutesBeforeMeet: Long, seconds: Long = 0) =
        meetAt.minusSeconds(minutesBeforeMeet * 60).plusSeconds(seconds)

    @Test
    fun `열리기 전이면 몇 분 뒤 열리는지(올림) 알린다`() {
        assertEquals(LobbyAvailability.NotYet(10), LobbyAvailability.of(voting, at(40)))
        assertEquals(LobbyAvailability.NotYet(1), LobbyAvailability.of(voting, at(30, seconds = -1)))
        assertEquals(LobbyAvailability.NotYet(150), LobbyAvailability.of(voting, at(180)))
    }

    @Test
    fun `30분 전부터 모집 일시 직전까지 열린다`() {
        assertEquals(LobbyAvailability.Open, LobbyAvailability.of(voting, at(30)))
        assertEquals(LobbyAvailability.Open, LobbyAvailability.of(voting, at(0, seconds = -1)))
        assertEquals(LobbyAvailability.Open, LobbyAvailability.of(voting.copy(status = null), at(5)))
    }

    @Test
    fun `모집 일시가 지났거나 시작·끝·취소면 닫힌다`() {
        assertEquals(LobbyAvailability.Closed, LobbyAvailability.of(voting, at(0)))
        assertEquals(
            LobbyAvailability.Closed,
            LobbyAvailability.of(voting.copy(status = CrewRunStatus.LOBBY_OPEN), at(-1))
        )
        listOf(CrewRunStatus.RUNNING, CrewRunStatus.FINISHED, CrewRunStatus.CANCELLED).forEach {
            assertEquals(LobbyAvailability.Closed, LobbyAvailability.of(voting.copy(status = it), at(10)), "$it")
        }
    }

    @Test
    fun `서버가 대기실 열림이라 하면 추정 시각이 조금 일러도 연다`() {
        assertEquals(
            LobbyAvailability.Open,
            LobbyAvailability.of(voting.copy(status = CrewRunStatus.LOBBY_OPEN), at(31))
        )
    }
}
