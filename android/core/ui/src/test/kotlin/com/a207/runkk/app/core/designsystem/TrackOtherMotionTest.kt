package com.a207.runkk.app.core.designsystem

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 트랙 상대의 등장·사라짐·큰 변화(S15P31A207-335 4차). 60 fps로 (초, 보이는 자리 m, 불투명도)를 기록해 보이는 동안 한 프레임
 * 0.75 m(트랙 폭 30 m의 2.5%)를 넘게 옮기지 않고, 등장 중 내 자리(0 m)를 가로지르지 않는지 본다.
 */
class TrackOtherMotionTest {
    private val frame = 1.0 / 60

    /**
     * [gapAt] 격차를 매 프레임 넣으며(같은 값은 무시된다) [until]초까지 그린다. 몸은 [readyAt]초에 준비되고 트랙 안 여부는 [isOnTrack]이다.
     */
    private fun play(
        until: Double,
        readyAt: Double = 0.0,
        motion: TrackOtherMotion = TrackOtherMotion(),
        isOnTrack: (Double) -> Boolean = { true },
        gapAt: (Double) -> Double
    ): List<Triple<Double, Double, Float>> {
        val rows = mutableListOf<Triple<Double, Double, Float>>()
        var t = 0.0
        while (t <= until) {
            motion.receive(gapAt(t), t)
            motion.frame(t, isReady = t >= readyAt, isOnTrack = isOnTrack(t))
            rows += Triple(t, motion.meters, motion.alpha)
            t += frame
        }
        return rows
    }

    private fun perSecond(vararg values: Double): (Double) -> Double =
        { t -> values[t.toInt().coerceAtMost(values.lastIndex)] }

    private fun visibleSteps(rows: List<Triple<Double, Double, Float>>) =
        rows.zipWithNext().filter { (a, b) -> a.third > 0f && b.third > 0f }.map { (a, b) ->
            b.first to
                b.second - a.second
        }

    private fun assertNoTeleport(rows: List<Triple<Double, Double, Float>>) {
        val over = visibleSteps(rows).filter { abs(it.second) > MAX_STEP + 1e-9 }
        assertEquals(emptyList(), over, "보이는 채로 한 프레임 0.75 m 넘게 옮김")
    }

    @Test
    fun `격차 0에서 몸이 준비되면 출발 쪽 밖(-18 m)에서 감속하며 들어와 내 자리를 넘지 않는다`() {
        val rows = play(until = 3.0, readyAt = 0.7) { 0.0 }

        val visible = rows.filter { it.third > 0f }
        assertEquals(-18.0, visible.first().second)
        assertTrue(visible.first().first < 0.7 + 2 * frame, "준비되면 바로 들어온다")
        assertTrue(visible.all { it.second <= 1e-9 }, "내 자리를 넘지 않는다")
        visibleSteps(rows).forEach { (t, step) -> assertTrue(step >= -1e-9, "되돌아가지 않는다 at $t") }
        assertNoTeleport(rows)
        assertEquals(0.0, rows.last().second, 1e-6)
        assertTrue(rows.first { it.second > -1e-6 && it.third > 0f }.first <= 0.7 + 1.0, "1초 안에 제자리에 닿는다")
    }

    @Test
    fun `몸 준비 때 이미 앞이면 그 자리에서 0점2초 동안 서서히 나타나고 왼쪽으로 가지 않는다`() {
        // 과거 기록이 약 3.5 m/s로 앞서 나가고 5초에 18 m(트랙 밖)가 된다.
        val rows = play(
            until = 5.5,
            readyAt = 4.07,
            isOnTrack = { it < 5.0 },
            gapAt = perSecond(0.0, 4.0, 7.0, 11.0, 14.0, 18.0)
        )

        val visible = rows.filter { it.third > 0f }
        assertTrue(visible.first().second > 12.0, "실제 자리(앞)에서 나타난다: ${visible.first()}")
        assertTrue(visible.all { it.second > 0.0 }, "내 자리 뒤로 가지 않는다")
        val opaque = rows.first { it.third == 1f }.first
        assertTrue(opaque - visible.first().first <= 0.2 + frame + 1e-9, "짧게 나타난다: ${opaque - visible.first().first}초")
        val backward = visibleSteps(rows).filter { it.second < -0.01 }
        assertEquals(emptyList(), backward, "왼쪽으로 미끄러짐")
        assertNoTeleport(rows)
    }

    @Test
    fun `몸 준비 때 뒤면 출발 쪽 밖에서 들어오고 내 자리를 넘지 않는다`() {
        val rows = play(until = 6.0, readyAt = 3.07, gapAt = perSecond(0.0, -3.0, -6.0, -8.0))

        val visible = rows.filter { it.third > 0f }
        assertEquals(-18.0, visible.first().second)
        assertTrue(visible.all { it.second < 0.0 }, "내 자리를 넘지 않는다")
        assertNoTeleport(rows)
        assertEquals(-8.0, rows.last().second, 0.05)
    }

    @Test
    fun `실제 자리가 트랙 밖이면 몸이 준비돼도 그리지 않는다`() {
        val rows = play(until = 3.0, isOnTrack = { false }) { -12.0 }

        assertTrue(rows.all { it.third == 0f })
    }

    @Test
    fun `보이는 동안 트랙 밖으로 나가면 따라가며 서서히 사라진다`() {
        val rows = play(until = 4.0, isOnTrack = { it < 2.0 }, gapAt = perSecond(12.0, 13.0, 14.0, 16.0, 17.0))

        val gone = rows.first { it.first >= 2.0 && it.third == 0f }.first
        assertTrue(gone - 2.0 <= 0.2 + 2 * frame, "0.2초 안에 사라진다: ${gone - 2.0}")
        assertTrue(rows.filter { it.first > gone }.all { it.third == 0f }, "다시 그리지 않는다")
        assertNoTeleport(rows)
    }

    @Test
    fun `보이는 동안 격차가 크게 바뀌면 그 자리에서 사라졌다가 다 사라진 프레임에 옮기고 새 자리에서 나타난다`() {
        val rows = play(until = 4.0, gapAt = perSecond(2.0, 2.0, -8.0))

        assertNoTeleport(rows)
        val fading = rows.filter { it.first >= 2.0 && it.first < 2.2 }
        assertTrue(fading.all { abs(it.second - 2.0) < 1e-6 }, "사라지는 동안 옛 자리에 선다")
        val moved = rows.first { it.first >= 2.0 && abs(it.second + 8.0) < 1e-6 }
        assertEquals(0f, moved.third, "옮기는 프레임은 보이지 않는다")
        assertEquals(1f, rows.last().third)
        assertEquals(-8.0, rows.last().second, 1e-6)
        assertTrue(visibleSteps(rows).none { it.second < -0.01 }, "왼쪽으로 미끄러지지 않는다")
    }

    @Test
    fun `격차 변화가 4점5 m 이하면 사라지지 않고 상한 안에서 따라간다`() {
        val rows = play(until = 4.0, gapAt = perSecond(2.0, 2.0, 6.0))

        assertTrue(rows.filter { it.first >= 1.0 }.all { it.third == 1f }, "보이는 채로 따라간다")
        assertNoTeleport(rows)
    }

    @Test
    fun `격차를 잃었다가 다시 알면 출발 쪽에서 들어오지 않고 그 자리에서 나타난다`() {
        val motion = TrackOtherMotion()
        play(until = 2.0, motion = motion) { 0.0 }
        motion.forget()
        assertEquals(0f, motion.alpha, "모르게 되면 바로 숨는다")

        motion.receive(0.0, 3.0)
        motion.frame(3.0, isReady = true, isOnTrack = true)
        motion.frame(3.0 + frame, isReady = true, isOnTrack = true)
        assertEquals(0.0, motion.meters, 1e-9)
        assertTrue(motion.alpha in 0f..0.5f)
    }

    private companion object {
        const val MAX_STEP = 0.75
    }
}
