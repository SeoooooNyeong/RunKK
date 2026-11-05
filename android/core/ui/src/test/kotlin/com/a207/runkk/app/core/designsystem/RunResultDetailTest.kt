package com.a207.runkk.app.core.designsystem

import com.a207.runkk.app.core.model.Pace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 러닝 결과 공용 부품(FR-289, D-502): 구간 페이스에서 가장 빠른 구간을 고른다. */
class RunResultDetailTest {
    @Test
    fun `TC-5826 가장 빠른 구간은 최소 초이고 같으면 앞 구간이다`() {
        assertEquals(1, fastestSplitIndex(listOf(Pace(360), Pace(340), Pace(350))))
        assertEquals(0, fastestSplitIndex(listOf(Pace(340), Pace(340), Pace(360))))
        assertNull(fastestSplitIndex(emptyList()))
        assertEquals(0, fastestSplitIndex(listOf(Pace(330))))
    }
}
