package com.a207.runkk.app.core.model

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaceTest {
    @Test
    fun `경과 시간 나누기 거리`() {
        assertEquals(Pace(300), Pace.of(Duration.ofMinutes(25), Distance(5_000)))
        assertEquals(Pace(333), Pace.of(Duration.ofSeconds(100), Distance(300)))
    }

    @Test
    fun `거리 0이면 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { Pace.of(Duration.ofSeconds(10), Distance.ZERO) }
    }

    @Test
    fun `secondsPerKm는 0보다 커야 한다`() {
        assertFailsWith<IllegalArgumentException> { Pace(0) }
    }

    @Test
    fun `목표보다 10퍼센트 이상 느린가 (FR-044)`() {
        val target = Pace(300)
        assertTrue(Pace(330).isSlowerThanBy(target, 0.1))
        assertFalse(Pace(329).isSlowerThanBy(target, 0.1))
    }

    @Test
    fun `범위와 시속`() {
        assertTrue(Pace(300).isWithin(Pace(180), Pace(900)))
        assertFalse(Pace(170).isWithin(Pace(180), Pace(900)))
        assertEquals(12.0, Pace(300).speedKmh())
    }
}
