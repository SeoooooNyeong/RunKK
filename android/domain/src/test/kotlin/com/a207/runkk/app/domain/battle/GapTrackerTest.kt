package com.a207.runkk.app.domain.battle

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.live.BattleGuidance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** GapTracker: 기록 대결의 격차·추월(10m 넘는 차로 선두가 바뀔 때만, FR-188)·도착 판정. */
class GapTrackerTest {
    @Test
    fun `10m 넘는 차로 선두가 바뀔 때만 추월·역전을 알린다`() {
        val tracker = GapTracker(Distance(1_000))
        assertNull(tracker.update(Distance(50), Distance(45)))
        assertEquals(5, tracker.gapMeters)
        assertNull(tracker.update(Distance(60), Distance(45)))
        assertNull(tracker.update(Distance(60), Distance(65)))
        assertEquals(BattleGuidance.Kind.OVERTAKEN, tracker.update(Distance(60), Distance(75)))
        assertEquals(-15, tracker.gapMeters)
        assertNull(tracker.update(Distance(70), Distance(76)))
        assertEquals(BattleGuidance.Kind.OVERTOOK, tracker.update(Distance(90), Distance(76)))
    }

    @Test
    fun `대결 거리에 처음 닿으면 도착`() {
        val tracker = GapTracker(Distance(1_000))
        assertFalse(tracker.hasArrived(Distance(999)))
        assertTrue(tracker.hasArrived(Distance(1_000)))
    }
}
