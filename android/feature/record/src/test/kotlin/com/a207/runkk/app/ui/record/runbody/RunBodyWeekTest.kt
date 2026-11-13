package com.a207.runkk.app.ui.record.runbody

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.ui.record.summary
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import org.junit.Test

/** 요약 주간 러닝 기록·페이스 축, 월간 주간 거리 축(G-14·G-22) 셈. */
class RunBodyWeekTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val monday = LocalDate.of(2026, 11, 2)

    private fun run(id: String, meters: Long, at: String, minutes: Long = 20, isValid: Boolean = true) =
        summary(id, 1, meters, isValid = isValid).copy(
            startedAt = Instant.parse(at),
            elapsed = Duration.ofMinutes(minutes)
        )

    @Test
    fun `주간 기록은 기기 시간대로 그 주(월~일)의 유효 러닝만 더하고 요일별 거리를 둔다`() {
        val runs = listOf(
            // 한국 11/2(월) 07:00, 4.2km
            run("mon", 4_200, "2026-11-01T22:00:00Z", minutes = 24),
            // 한국 11/4(수) 두 번 2.5km
            run("wed1", 2_500, "2026-11-04T00:00:00Z", minutes = 15),
            run("wed2", 2_500, "2026-11-04T10:00:00Z", minutes = 15),
            // 집계 제외 기록은 뺀다
            run("bad", 9_000, "2026-11-05T00:00:00Z", isValid = false),
            // 한국 11/1(일) 23:30은 지난주, 한국 11/9(월) 00:10은 다음 주
            run("before", 3_000, "2026-11-01T14:30:00Z"),
            run("after", 3_000, "2026-11-08T15:10:00Z")
        )

        val week = RunBodyWeek.of(runs, monday, seoul)

        assertEquals(Distance(9_200), week.distance)
        assertEquals(3, week.runs)
        assertEquals(Duration.ofMinutes(54), week.elapsed)
        assertEquals(listOf(Distance(4_200), null, Distance(5_000), null, null, null, null), week.days)
    }

    @Test
    fun `같은 기록도 기기 시간대가 다르면 다른 날로 센다`() {
        val one = listOf(run("r", 5_000, "2026-11-01T22:00:00Z"))

        // 런던에서는 11/1(일) 22:00이라 지난주다
        assertEquals(0, RunBodyWeek.of(one, monday, ZoneId.of("Europe/London")).runs)
        assertEquals(1, RunBodyWeek.of(one, monday, seoul).runs)
    }

    @Test
    fun `주 이름은 목요일이 속한 달의 몇째 주이고 읽는 범위는 앞뒤 하루를 더한다`() {
        assertEquals(11 to 1, weekOfMonth(monday))
        assertEquals(10 to 3, weekOfMonth(LocalDate.of(2026, 10, 12)))
        // 9/28(월)~10/4(일)은 목요일 10/1이 10월이라 10월 1주
        assertEquals(10 to 1, weekOfMonth(LocalDate.of(2026, 9, 28)))
        assertEquals(LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 9), weekQueryRange(monday))
        assertEquals(monday, weekStartOf(LocalDate.of(2026, 11, 8)))
    }

    @Test
    fun `페이스 축은 두 값을 감싸는 가장 가까운 30초 눈금이고 눈금에 딱 맞으면 한 칸 바깥이다`() {
        assertEquals(390 to 330, paceAxisRange(Pace(362), Pace(348)))
        assertEquals(390 to 300, paceAxisRange(Pace(360), Pace(330)))
        assertEquals(360 to 330, paceAxisRange(Pace(348), Pace(348)))
    }

    @Test
    fun `주간 거리 막대 축은 가장 긴 주를 5km 단위로 올린다`() {
        assertEquals(25_000L, weekAxisMeters(20_200))
        assertEquals(20_000L, weekAxisMeters(20_000))
        assertEquals(5_000L, weekAxisMeters(0))
    }
}
