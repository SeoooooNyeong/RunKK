package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.domain.crew.CrewRunStanding.Mate
import com.a207.runkk.app.domain.live.CrewRunRelay.State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 크루런 트랙 보기의 순위·가장 가까운 사람·앞뒤 인원([임시:D-성-267]). 화면 표시에만 쓴다. */
class CrewRunStandingTest {
    private fun mate(id: Long, meters: Int, state: State = State.RUNNING) = Mate(id, "크루원$id", meters, state)

    @Test
    fun `가장 가까운 사람을 고른다`() {
        val standing = CrewRunStanding.of(1_000, listOf(mate(1, 1_200), mate(2, 988), mate(3, 400)))

        assertEquals(2L, standing.nearest?.memberId)
        assertEquals(-12, standing.nearestGapMeters, "크루원 − 나: 뒤면 음수")
    }

    @Test
    fun `동률이면 앞선 사람이 먼저다`() {
        val standing = CrewRunStanding.of(1_000, listOf(mate(1, 990), mate(2, 1_010), mate(3, 1_010)))

        assertEquals(2L, standing.nearest?.memberId, "같은 차이면 앞선 쪽, 그다음은 입장 순")
        assertEquals(10, standing.nearestGapMeters)
    }

    @Test
    fun `앞 n명 뒤 n명을 센다`() {
        val mates = listOf(mate(1, 1_500), mate(2, 1_300), mate(3, 1_012), mate(4, 900), mate(5, 700), mate(6, 1_000))
        val standing = CrewRunStanding.of(1_000, mates)

        assertEquals(6L, standing.nearest?.memberId, "같은 거리인 사람이 가장 가깝다")
        assertEquals(3, standing.ahead)
        assertEquals(2, standing.behind)
        val nearAhead = CrewRunStanding.of(1_000, mates.dropLast(1))
        assertEquals(3L, nearAhead.nearest?.memberId)
        assertEquals(2, nearAhead.ahead, "트랙에 그리는 가장 가까운 사람은 칩 인원에서 뺀다")
        assertEquals(2, nearAhead.behind)
    }

    @Test
    fun `내 순위와 인원`() {
        val standing = CrewRunStanding.of(1_000, listOf(mate(1, 1_500), mate(2, 1_500), mate(3, 1_000), mate(4, 10)))

        assertEquals(3, standing.rank, "나보다 앞선 두 명 다음이고 같은 거리는 같은 순위다")
        assertEquals(5, standing.total, "나를 더한 인원")
        val alone = CrewRunStanding.of(0, emptyList())
        assertEquals(1, alone.rank)
        assertEquals(1, alone.total)
        assertNull(alone.nearest)
        assertNull(alone.nearestGapMeters)
        assertEquals(0, alone.ahead)
        assertEquals(0, alone.behind)
    }

    @Test
    fun `끝낸 사람은 후보에서 뺀다`() {
        val mates = listOf(
            mate(1, 1_001, State.ENDED),
            mate(2, 1_002, State.CHECKING),
            mate(3, 1_100, State.PAUSED),
            mate(4, 700)
        )
        val standing = CrewRunStanding.of(1_000, mates)

        assertEquals(3L, standing.nearest?.memberId, "달리는 중이거나 일시정지인 사람만 후보다")
        assertEquals(4, standing.rank, "끝낸 사람과 연결 확인 중인 사람도 순위에는 센다")
        assertEquals(2, standing.ahead, "끝낸 사람과 연결 확인 중인 사람은 칩 인원에 남는다")
        assertEquals(1, standing.behind)
        assertNull(CrewRunStanding.of(1_000, mates.take(2)).nearest, "후보가 없으면 가장 가까운 사람이 없다")
    }
}
