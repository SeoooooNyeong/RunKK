package com.a207.runkk.app.domain.run

import com.a207.runkk.app.domain.testing.T0
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * MR !91 AN-5: 움직임 표본이 서버 상한(86,400개)을 넘으면 기록 전체가 거부되지 않게 업로드 전에 같은 시간 칸끼리 합쳐 상한 안으로 줄인다.
 */
class MotionThinningTest {
    private fun sample(millis: Long, speed: Double? = null, cadence: Double? = null) =
        MotionSample(T0.plusMillis(millis), speedMps = speed, cadenceSpm = cadence)

    @Test
    fun `상한 안이면 그대로 둔다`() {
        val samples = List(10) { sample(it * 1_000L, speed = 3.0) }
        assertSame(samples, samples.thinnedTo(10))
    }

    @Test
    fun `상한을 넘으면 1초 칸으로 합쳐 속도·케이던스를 평균한다`() {
        // 1초에 속도 2개·케이던스 1개 = 3개씩, 4초
        val samples = (0 until 4).flatMap { s ->
            listOf(
                sample(s * 1_000L, speed = 3.0),
                sample(s * 1_000L + 400, speed = 4.0),
                sample(s * 1_000L + 700, cadence = 170.0)
            )
        }

        val thinned = samples.thinnedTo(5)

        assertEquals(4, thinned.size)
        assertEquals(sample(0, speed = 3.5, cadence = 170.0), thinned.first())
        assertEquals(T0.plusSeconds(3), thinned.last().at)
    }

    @Test
    fun `1초 칸으로도 넘치면 칸을 두 배씩 넓혀 상한 안에 든다`() {
        val samples = List(100) { sample(it * 1_000L, speed = it.toDouble()) }

        val thinned = samples.thinnedTo(30)

        assertTrue(thinned.size <= 30, "실제 ${thinned.size}개")
        assertEquals(25, thinned.size, "4초 칸")
        assertEquals(sample(0, speed = 1.5), thinned.first())
        assertTrue(thinned.zipWithNext().all { (a, b) -> a.at < b.at }, "시간순")
    }

    @Test
    fun `서버 상한은 위치 상한(43,200)의 두 배다`() {
        assertEquals(86_400, MAX_UPLOAD_MOTIONS)
    }
}
