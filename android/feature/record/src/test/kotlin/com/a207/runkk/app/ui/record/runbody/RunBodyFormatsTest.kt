package com.a207.runkk.app.ui.record.runbody

import com.a207.runkk.app.core.model.Pace
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

/** 피그마 런바디 표기(S15P31A207-294): 페이스는 분·초 기호(′ ″), 활동 시간은 시:분:초 두 자리. */
class RunBodyFormatsTest {
    @Test
    fun `페이스는 피그마처럼 프라임 기호로 적는다`() {
        assertEquals("5′48″", runBodyPace(Pace(348)))
        assertEquals("6′02″", runBodyPace(Pace(362)))
        assertEquals("-′--″", runBodyPace(null))
    }

    @Test
    fun `활동 시간은 1시간 미만이어도 00시로 적는다(G-20)`() {
        assertEquals("00:30:10", runBodyClock(Duration.ofSeconds(30 * 60 + 10)))
        assertEquals("01:02:03", runBodyClock(Duration.ofSeconds(3_723)))
        assertEquals("00:00:00", runBodyClock(Duration.ofSeconds(-5)))
    }

    @Test
    fun `주간 거리 막대는 가장 긴 주 하나만 라임으로 고른다(G-22)`() {
        assertEquals(1, longestWeekIndex(listOf(4_000L, 20_200L, 9_000L, 20_200L)))
        assertEquals(null, longestWeekIndex(listOf(0L, 0L)))
        assertEquals(null, longestWeekIndex(emptyList()))
    }
}
