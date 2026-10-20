package com.a207.runkk.app.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DistanceTest {
    @Test
    fun `음수 거리는 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { Distance.ofMeters(-1) }
    }

    @Test
    fun `km로 만들면 미터로 반올림한다`() {
        assertEquals(Distance(5_000), Distance.ofKm(5.0))
        assertEquals(Distance(1_235), Distance.ofKm(1.2345))
    }

    @Test
    fun `빼기 결과가 음수면 0이다`() {
        assertEquals(Distance.ZERO, Distance(100).minus(Distance(200)))
        assertEquals(Distance(300), Distance(100).plus(Distance(200)))
    }

    @Test
    fun `km는 소수 둘째 자리로 반올림한다`() {
        assertEquals(1.24, Distance(1_235).km())
    }

    @Test
    fun `비교`() {
        assertTrue(Distance(1_000).isAtLeast(Distance(1_000)))
        assertFalse(Distance(999).isAtLeast(Distance(1_000)))
        assertTrue(Distance(1) < Distance(2))
    }
}
