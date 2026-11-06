package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.record.Ranking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 랭킹 시상대(FR-290, J24-15): 앞 세 행은 시상대, 목록은 4위부터, 받침 숫자는 행의 순위 그대로. */
class RankingPodiumTest {
    private fun entries(vararg ranks: Int) =
        ranks.mapIndexed { index, rank -> Ranking.Entry(rank, "러너${index + 1}", Distance(50_000L - index * 1_000)) }

    @Test
    fun `TC-5855 시상대는 앞 세 행이고 목록은 4위부터다`() {
        val none = RankingPodium.of(emptyList())
        assertNull(none.first)
        assertNull(none.second)
        assertNull(none.third)
        assertEquals(emptyList(), none.rest)

        val one = RankingPodium.of(entries(1))
        assertEquals("러너1", one.first?.name)
        assertNull(one.second)
        assertNull(one.third)

        val two = RankingPodium.of(entries(1, 2))
        assertEquals(listOf("러너1", "러너2"), listOfNotNull(two.first, two.second).map { it.name })
        assertNull(two.third)

        val three = RankingPodium.of(entries(1, 2, 3))
        assertEquals("러너3", three.third?.name)
        assertEquals(emptyList(), three.rest)

        val five = RankingPodium.of(entries(1, 2, 3, 4, 5))
        assertEquals(listOf(4, 5), five.rest.map { it.rank })
    }

    @Test
    fun `TC-5856 동순위는 받침 숫자에 실제 순위를 그대로 쓴다`() {
        val podium = RankingPodium.of(entries(1, 2, 2, 4, 5))

        assertEquals(listOf(1, 2, 2), listOfNotNull(podium.first, podium.second, podium.third).map { it.rank })
        assertEquals(4, podium.rest.first().rank)
    }
}
