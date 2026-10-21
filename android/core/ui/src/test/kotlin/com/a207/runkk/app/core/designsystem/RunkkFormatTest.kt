package com.a207.runkk.app.core.designsystem

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class RunkkFormatTest {
    @Test
    fun `거리는 km 소수 둘째·첫째 자리로 보인다`() {
        assertEquals("5.02", RunkkFormat.km(Distance(5_020)))
        assertEquals("0.00", RunkkFormat.km(Distance.ZERO))
        assertEquals("10.2", RunkkFormat.kmShort(Distance(10_190)))
    }

    @Test
    fun `목표 거리 라벨은 정수면 소수 없이`() {
        assertEquals("5K", RunkkFormat.targetLabel(Distance(5_000)))
        assertEquals("2.5K", RunkkFormat.targetLabel(Distance(2_500)))
    }

    @Test
    fun `페이스는 m·ss, 없으면 빈 표시`() {
        assertEquals("5:32", RunkkFormat.pace(Pace(332)))
        assertEquals("-:--", RunkkFormat.pace(null))
        assertEquals("1킬로미터당 5분 32초", RunkkFormat.paceSpoken(Pace(332)))
    }

    @Test
    fun `경과 시간은 한 시간 미만 mm·ss, 넘으면 h·mm·ss`() {
        assertEquals("27:48", RunkkFormat.duration(Duration.ofSeconds(27 * 60 + 48L)))
        assertEquals("1:02:03", RunkkFormat.duration(Duration.ofSeconds(3_723)))
        assertEquals("00:00", RunkkFormat.duration(Duration.ofSeconds(-5)))
        assertEquals("1시간 2분 3초", RunkkFormat.durationSpoken(Duration.ofSeconds(3_723)))
    }
}
