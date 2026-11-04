package com.a207.runkk.app.domain.battle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 랜덤 대결 잠금 판단(D-500, J24-12): 폰 잠금은 없고 워치 랜덤 대결만 배치 중일 때 잠근다. */
class RandomBattleGateTest {
    @Test
    fun `TC-5864 워치 랜덤 대결은 배치 중일 때만 잠근다`() {
        assertTrue(RandomBattleGate.isWatchRandomLocked("UNRANKED"))
        assertFalse(RandomBattleGate.isWatchRandomLocked("GOLD"))
        assertFalse(RandomBattleGate.isWatchRandomLocked(null), "모르면 잠그지 않는다")
    }

    @Test
    fun `배치고사 판 수는 3이다(진행 n의 분모)`() {
        assertEquals(3, RandomBattleGate.PLACEMENT_RUNS)
    }
}
