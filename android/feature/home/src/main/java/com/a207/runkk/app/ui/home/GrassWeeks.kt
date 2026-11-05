package com.a207.runkk.app.ui.home

import com.a207.runkk.app.core.model.Distance
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 러닝 잔디(최근 5주) 칸 만들기(FR-287). */
object GrassWeeks {
    private const val WEEKS = 5L
    private const val DAYS = 7L

    /**
     * 이번 주를 포함한 5주를 월요일부터 채운다(5행 × 7열, 마지막 행이 이번 주). 그날 유효 거리가 0보다 크면 달린 날,
     * 오늘 뒤는 달렸어도 미래 칸이다. 5주 밖 날짜는 쓰지 않는다.
     */
    fun of(grass: Map<LocalDate, Distance>, today: LocalDate): List<List<GrassCell>> {
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val first = thisMonday.minusWeeks(WEEKS - 1)
        return (0 until WEEKS).map { week ->
            (0 until DAYS).map { day ->
                val date = first.plusWeeks(week).plusDays(day)
                val state = when {
                    date.isAfter(today) -> GrassState.FUTURE
                    (grass[date]?.meters ?: 0L) > 0 -> GrassState.RAN
                    else -> GrassState.EMPTY
                }
                GrassCell(date, state, isToday = date == today)
            }
        }
    }
}
