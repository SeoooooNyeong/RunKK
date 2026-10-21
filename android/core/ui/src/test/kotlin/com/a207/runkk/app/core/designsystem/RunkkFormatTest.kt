package com.a207.runkk.app.core.designsystem

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RunkkFormatTest {
    private val res get() = RuntimeEnvironment.getApplication().resources

    @Test
    fun `거리는 km 소수 둘째·첫째 자리로 보인다`() {
        assertEquals("5.02", RunkkFormat.km(Distance(5_020)))
        assertEquals("0.00", RunkkFormat.km(Distance.ZERO))
        assertEquals("10.2", RunkkFormat.kmShort(Distance(10_190)))
    }

    @Test
    fun `영문 오버라인·거리 라벨은 보조 기술이 한글로 읽는다`() {
        assertEquals("실시간 대결, 5킬로미터", RunkkFormat.spokenLabel(res, "LIVE BATTLE · 5K"))
        assertEquals("나와의 싸움, 하프", RunkkFormat.spokenLabel(res, "SELF BATTLE · 하프"))
        assertEquals("유명인 대결, 10킬로미터", RunkkFormat.spokenLabel(res, "CELEB BATTLE · 10K"))
        assertEquals("자유런, 목표 없음", RunkkFormat.spokenLabel(res, "FREE RUN · FREE"))
        assertEquals("크루런 결과", RunkkFormat.spokenLabel(res, "CREW RUN RESULT"))
        assertEquals("크루런, 2.5킬로미터", RunkkFormat.spokenLabel(res, "CREW RUN · 2.5K"))
        assertEquals("배틀런", RunkkFormat.spokenLabel(res, "BATTLE RUN"))
        assertEquals("매칭 성사", RunkkFormat.spokenLabel(res, "MATCHED"))
        assertEquals("5킬로미터", RunkkFormat.spokenLabel(res, "5K"))
        assertEquals("GPS 좋음", RunkkFormat.spokenLabel(res, "GPS 좋음"))
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
        assertEquals("1킬로미터당 5분 32초", RunkkFormat.paceSpoken(res, Pace(332)))
    }

    @Test
    fun `경과 시간은 한 시간 미만 mm·ss, 넘으면 h·mm·ss`() {
        assertEquals("27:48", RunkkFormat.duration(Duration.ofSeconds(27 * 60 + 48L)))
        assertEquals("1:02:03", RunkkFormat.duration(Duration.ofSeconds(3_723)))
        assertEquals("00:00", RunkkFormat.duration(Duration.ofSeconds(-5)))
        assertEquals("1시간 2분 3초", RunkkFormat.durationSpoken(res, Duration.ofSeconds(3_723)))
    }
}
