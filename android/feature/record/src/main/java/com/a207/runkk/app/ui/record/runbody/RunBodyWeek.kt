package com.a207.runkk.app.ui.record.runbody

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.record.RunSummary
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 요약 "주간 러닝 기록"(G-14): 월~일 한 주의 유효 러닝 합계와 요일별 거리. 날짜는 기기 시간대([ZoneId])로 가른다. 서버 기록 목록은 서버
 * 날짜(한국)로 거르므로 앞뒤 하루를 더 읽고 여기서 다시 거른다([weekQueryRange]).
 *
 * @property days 월요일부터 7칸. 달리지 않은 날은 null
 */
data class RunBodyWeek(val distance: Distance, val runs: Int, val elapsed: Duration, val days: List<Distance?>) {
    companion object {
        /** [runs] 중 [monday] 주에 시작한 유효 러닝. */
        fun of(runs: List<RunSummary>, monday: LocalDate, zone: ZoneId): RunBodyWeek {
            val sunday = monday.plusDays(DAYS - 1L)
            val inWeek = runs.filter { run ->
                val day = run.startedAt.atZone(zone).toLocalDate()
                run.isValid && day >= monday && day <= sunday
            }
            val byDay = inWeek.groupBy { run -> run.startedAt.atZone(zone).toLocalDate() }
            return RunBodyWeek(
                distance = Distance(inWeek.sumOf { it.distance.meters }),
                runs = inWeek.size,
                elapsed = inWeek.fold(Duration.ZERO) { sum, run -> sum + run.elapsed },
                days = (0 until DAYS).map { offset ->
                    byDay[monday.plusDays(offset.toLong())]?.let { day -> Distance(day.sumOf { it.distance.meters }) }
                }
            )
        }

        private const val DAYS = 7
    }
}

/** [date]가 속한 주의 월요일. */
internal fun weekStartOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** 서버 기록 목록에 넘길 날짜 범위: 기기 시간대와 서버 날짜가 하루 어긋나도 빠지지 않게 앞뒤 하루를 더한다. */
internal fun weekQueryRange(monday: LocalDate): Pair<LocalDate, LocalDate> =
    monday.minusDays(1) to monday.plusDays(WEEK_DAYS)

/**
 * "10월 3주": 그 주 목요일이 속한 달과 그 달의 몇째 주(크루 레이스 주 이름과 같은 규칙). 월요일이 지난달이어도 목요일이 이번 달이면 이번 달
 * 1주다.
 */
internal fun weekOfMonth(monday: LocalDate): Pair<Int, Int> {
    val thursday = monday.plusDays(THURSDAY_OFFSET)
    return thursday.monthValue to (thursday.dayOfMonth - 1) / WEEK_DAYS.toInt() + 1
}

private const val WEEK_DAYS = 7L
private const val THURSDAY_OFFSET = 3L
