package com.a207.runkk.app.ui.record.runbody

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/** MR !91 e2e 6: 부하 준비 중 카드의 지난 날 수는 오늘이 아니라 그 달을 셈한 시점(asOf)으로 센다. */
class RunBodyLoadDaysTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun `지난달은 그 달 기준 시점으로 센다`() {
        // 10월 월말(asOf 10-31 23:59 한국)에 켜지는 날이 11-10이면 28 − 10 = 18일 지났다
        val asOf = Instant.parse("2026-10-31T14:59:00Z")

        assertEquals(LocalDate.of(2026, 10, 31), loadBasisDate(asOf, zone, today))
        assertEquals(18, loadDaysPassed(LocalDate.of(2026, 11, 10), loadBasisDate(asOf, zone, today)))
    }

    @Test
    fun `기준 시점이 없으면 오늘로 센다`() {
        assertEquals(today, loadBasisDate(null, zone, today))
        assertEquals(26, loadDaysPassed(LocalDate.of(2026, 11, 10), today))
    }

    @Test
    fun `지난 날 수는 0~28 안이다`() {
        assertEquals(0, loadDaysPassed(LocalDate.of(2026, 12, 31), today))
        assertEquals(28, loadDaysPassed(LocalDate.of(2026, 10, 1), today))
    }
}
