package com.a207.runkk.app.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 티어 표시 규칙(FR-282, D-494, 05-03 5.7.3.5): 본인은 확정을 먼저·배치 중이면 임시 티어, 다른 사람은 임시 티어를 만들지 않는다. */
class TierDisplayTest {
    @Test
    fun `TC-5814 본인 티어 표시는 확정을 먼저, 배치 중이면 임시 티어를 보인다`() {
        assertNull(TierDisplay.ofSelf(null, null))
        assertEquals(TierDisplay.Placing, TierDisplay.ofSelf("UNRANKED", null))
        assertEquals(TierDisplay.Provisional("GOLD"), TierDisplay.ofSelf("UNRANKED", "GOLD"))
        assertEquals(TierDisplay.Confirmed("SILVER"), TierDisplay.ofSelf("SILVER", "GOLD"))
        assertEquals(TierDisplay.Confirmed("SILVER"), TierDisplay.ofSelf("SILVER", null))
    }

    @Test
    fun `TC-5815 공개 티어 표시는 임시 티어를 만들지 않는다`() {
        assertNull(TierDisplay.ofPublic(null))
        assertEquals(TierDisplay.Placing, TierDisplay.ofPublic("UNRANKED"))
        assertEquals(TierDisplay.Confirmed("GOLD"), TierDisplay.ofPublic("GOLD"))
        // 모르는 값은 확정으로 두고 그림은 TierEmblem이 배치 중 그림으로 그린다.
        assertEquals(TierDisplay.Confirmed("DIAMOND"), TierDisplay.ofPublic("DIAMOND"))
        listOf(null, "UNRANKED", "GOLD", "DIAMOND").forEach {
            assertTrue(TierDisplay.ofPublic(it) !is TierDisplay.Provisional, "$it")
        }
    }

    @Test
    fun `PL1-A8 모르는 임시 티어는 영문 이름 대신 배치 중으로 접는다`() {
        assertEquals(TierDisplay.Placing, TierDisplay.ofSelf("UNRANKED", "DIAMOND"))
        listOf("BRONZE", "SILVER", "GOLD", "PLATINUM", "MASTER").forEach {
            assertEquals(TierDisplay.Provisional(it), TierDisplay.ofSelf("UNRANKED", it))
        }
    }
}
