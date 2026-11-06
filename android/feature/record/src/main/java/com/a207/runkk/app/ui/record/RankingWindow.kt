package com.a207.runkk.app.ui.record

import com.a207.runkk.app.domain.record.AggregationPeriod
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 랭킹 집계 기간과 마감까지 남은 시간(랭킹 v3, 피그마 「11.3 – 11.9 · 2일 13시간 뒤 마감」). 서버 집계와 같이 한국 시각으로
 * 주간은 월요일~일요일, 월간은 1일~말일이고, 마감은 [end] 다음 날 0시다.
 */
data class RankingWindow(val start: LocalDate, val end: LocalDate, val remaining: Duration) {
    companion object {
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

        fun of(period: AggregationPeriod, now: Instant): RankingWindow {
            val today = now.atZone(SEOUL).toLocalDate()
            val start = when (period) {
                AggregationPeriod.WEEKLY -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                AggregationPeriod.MONTHLY -> today.withDayOfMonth(1)
            }
            val end = when (period) {
                AggregationPeriod.WEEKLY -> start.plusDays(DAYS_IN_WEEK - 1)
                AggregationPeriod.MONTHLY -> today.with(TemporalAdjusters.lastDayOfMonth())
            }
            val closes = end.plusDays(1).atStartOfDay(SEOUL).toInstant()
            return RankingWindow(start, end, Duration.between(now, closes).coerceAtLeast(Duration.ZERO))
        }

        private const val DAYS_IN_WEEK = 7L
    }
}
