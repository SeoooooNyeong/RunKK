package com.a207.runkk.app.core.designsystem

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 심박 구간 4단계(FR-289, J24-17, D-503): Z4·Z5를 합치고 정수 %의 합을 100으로 맞춘다. */
class HeartZoneBandsTest {
    private fun zones(vararg pairs: Pair<String, Long>): Map<String, Duration> =
        pairs.associate { (zone, seconds) -> zone to Duration.ofSeconds(seconds) }

    @Test
    fun `TC-5823 다섯 구간을 네 단계로 합치고 합은 100이다`() {
        val bands = HeartZoneBands.toFour(zones("Z1" to 60, "Z2" to 120, "Z3" to 180, "Z4" to 60, "Z5" to 180))

        assertEquals(
            listOf(ZoneShare("Z1", 10), ZoneShare("Z2", 20), ZoneShare("Z3", 30), ZoneShare("Z4", 40)),
            bands
        )
        assertEquals(100, bands.sumOf { it.percent })
    }

    @Test
    fun `TC-5824 반올림 나머지는 가장 큰 칸이 받는다`() {
        val bands = HeartZoneBands.toFour(zones("Z1" to 1, "Z2" to 1, "Z3" to 1))

        // 33.3%씩이면 합 99. 가장 큰 칸(동률이면 앞 단계)이 1을 더 받는다.
        assertEquals(listOf(34, 33, 33, 0), bands.map { it.percent })
        assertEquals(100, bands.sumOf { it.percent })
        assertTrue(bands.all { it.percent >= 0 })
        // 올림이 많아 101이 되면 가장 큰 칸에서 뺀다(16.7·16.7·16.7·50 → 17·17·17·50 = 101).
        val over = HeartZoneBands.toFour(zones("Z1" to 1, "Z2" to 1, "Z3" to 1, "Z5" to 3))
        assertEquals(listOf(17, 17, 17, 49), over.map { it.percent })
    }

    @Test
    fun `TC-5825 빈 구간은 빈 목록이다`() {
        assertEquals(emptyList(), HeartZoneBands.toFour(emptyMap()))
        assertEquals(emptyList(), HeartZoneBands.toFour(zones("Z1" to 0, "Z2" to 0)))
        // 모르는 키는 무시한다.
        assertEquals(
            listOf(100, 0, 0, 0),
            HeartZoneBands.toFour(zones("Z1" to 30, "Z9" to 600)).map { it.percent }
        )
        assertEquals(emptyList(), HeartZoneBands.toFour(zones("Z9" to 600)))
    }
}
