package com.a207.runkk.app.ui.home

import com.a207.runkk.app.core.model.Distance
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 러닝 잔디 5주(FR-287, J24-16): 이번 주 포함 5주, 월요일 시작, 오늘 뒤는 미래 칸. */
class GrassWeeksTest {
    private val wednesday = LocalDate.of(2026, 11, 4)

    @Test
    fun `TC-5832 이번 주를 포함한 5주를 월요일부터 채우고 오늘 뒤는 미래 칸이다`() {
        val weeks = GrassWeeks.of(emptyMap(), wednesday)

        assertEquals(5, weeks.size)
        assertTrue(weeks.all { it.size == 7 })
        // 2026-11-04는 수요일이라 이번 주 월요일은 11-02, 5주 전 월요일은 10-05다(시험 문서의 10-06은 화요일이라 바로잡음).
        assertEquals(LocalDate.of(2026, 10, 5), weeks.first().first().date)
        assertEquals(LocalDate.of(2026, 11, 8), weeks.last().last().date)
        val cells = weeks.flatten()
        assertEquals(listOf(wednesday), cells.filter { it.isToday }.map { it.date })
        assertTrue(weeks.last().indexOfFirst { it.isToday } == 2, "오늘은 마지막 행 수요일")
        assertEquals(
            listOf(5, 6, 7, 8).map { LocalDate.of(2026, 11, it) },
            cells.filter { it.state == GrassState.FUTURE }.map { it.date }
        )
    }

    @Test
    fun `TC-5833 오늘이 월요일이면 미래 6칸, 일요일이면 0칸이다`() {
        val monday = GrassWeeks.of(emptyMap(), LocalDate.of(2026, 11, 2)).flatten()
        val sunday = GrassWeeks.of(emptyMap(), LocalDate.of(2026, 11, 8)).flatten()

        assertEquals(6, monday.count { it.state == GrassState.FUTURE })
        assertEquals(LocalDate.of(2026, 11, 2), monday.first { it.isToday }.date)
        assertEquals(0, sunday.count { it.state == GrassState.FUTURE })
        assertTrue(sunday.last().isToday)
    }

    @Test
    fun `TC-5834 달린 거리가 0보다 크면 달린 날이다`() {
        val grass = mapOf(
            LocalDate.of(2026, 11, 2) to Distance.ZERO,
            LocalDate.of(2026, 11, 3) to Distance(1),
            LocalDate.of(2026, 11, 4) to Distance(5_000),
            LocalDate.of(2026, 10, 1) to Distance(5_000),
            LocalDate.of(2026, 11, 6) to Distance(5_000)
        )
        val byDate = GrassWeeks.of(grass, wednesday).flatten().associateBy { it.date }

        assertEquals(GrassState.EMPTY, byDate.getValue(LocalDate.of(2026, 11, 2)).state)
        assertEquals(GrassState.RAN, byDate.getValue(LocalDate.of(2026, 11, 3)).state)
        assertEquals(GrassState.RAN, byDate.getValue(wednesday).state)
        assertTrue(LocalDate.of(2026, 10, 1) !in byDate, "5주 밖은 쓰지 않는다")
        assertEquals(GrassState.FUTURE, byDate.getValue(LocalDate.of(2026, 11, 6)).state, "미래는 달렸어도 미래 칸")
        assertEquals(35, byDate.size)
    }
}
