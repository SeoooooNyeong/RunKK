package com.a207.runkk.app.core.model

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/** 서버 시각 추정: 응답 Date 머리글과 기기 시계의 차를 기기 시계에 더한다. */
class ServerClockTest {
    private val t0: Instant = Instant.parse("2026-10-20T00:00:00Z")
    private val device = object : Clock() {
        var now: Instant = t0

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now
    }
    private val clock = ServerClock(device)

    @Test
    fun `서버 시각을 받기 전에는 기기 시계 그대로다`() {
        assertEquals(t0, clock.now())
    }

    @Test
    fun `기기 시계가 서버보다 2분 빠르면 그만큼 빼서 본다`() {
        clock.onServerDate(t0.minusSeconds(120))
        assertEquals(t0.minusSeconds(120), clock.now())
        device.now = t0.plusSeconds(30)
        assertEquals(t0.minusSeconds(90), clock.now())
    }

    @Test
    fun `Date 머리글은 초 단위라 1초 미만의 차는 버린다`() {
        clock.onServerDate(t0.minusMillis(800))
        assertEquals(t0, clock.now())
        clock.onServerDate(t0.plusSeconds(5))
        clock.onServerDate(t0.plusSeconds(5).minusMillis(700))
        assertEquals(t0.plusSeconds(5), clock.now())
    }
}
