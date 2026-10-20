package com.a207.runkk.watch.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** 워치 화면 숫자 표기: 경과 시간·거리·페이스·심박(빈칸 포함). */
class WatchFormatTest {
    @Test
    fun `경과 시간은 한 시간 전에는 분초, 넘으면 시분초`() {
        assertEquals("0:00", WatchFormat.elapsed(0))
        assertEquals("5:07", WatchFormat.elapsed(307))
        assertEquals("59:59", WatchFormat.elapsed(3_599))
        assertEquals("1:00:00", WatchFormat.elapsed(3_600))
        assertEquals("1:02:03", WatchFormat.elapsed(3_723))
    }

    @Test
    fun `음수 경과 시간은 0으로 본다`() {
        assertEquals("0:00", WatchFormat.elapsed(-5))
    }

    @Test
    fun `거리는 km 소수 둘째 자리까지 내림`() {
        assertEquals("0.00", WatchFormat.distanceKm(0))
        assertEquals("0.00", WatchFormat.distanceKm(9))
        assertEquals("4.32", WatchFormat.distanceKm(4_329))
        assertEquals("10.00", WatchFormat.distanceKm(10_000))
    }

    @Test
    fun `페이스는 분과 초, 없으면 빈칸`() {
        assertEquals("5'15\"", WatchFormat.pace(315))
        assertEquals("6'05\"", WatchFormat.pace(365))
        assertEquals(WatchFormat.EMPTY, WatchFormat.pace(null))
        assertEquals(WatchFormat.EMPTY, WatchFormat.pace(0))
    }

    @Test
    fun `심박은 없으면 빈칸`() {
        assertEquals("152", WatchFormat.heartRate(152))
        assertEquals(WatchFormat.EMPTY, WatchFormat.heartRate(null))
    }
}
