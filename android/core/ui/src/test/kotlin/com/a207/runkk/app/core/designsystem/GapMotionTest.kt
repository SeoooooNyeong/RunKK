package com.a207.runkk.app.core.designsystem

import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 트랙 상대 위치 보간(S15P31A207-335): 1초마다 정수 m로 오는 거리 차를 매 프레임 부드럽게 보이게 한다. 받은 값 사이는 상대 속도로
 * 예측하고(|v| ≤ 4 m/s, 1.5초까지), 보이는 값은 약 250 ms 시간 상수로 예측값을 따라간다.
 */
class GapMotionTest {
    private val frame = 1.0 / 60

    /** [values]를 1초마다 받으며 60 fps로 그린 보이는 값들(시각, 값). [until]초까지 그린다. */
    private fun play(values: List<Double>, until: Double = values.size.toDouble()): List<Pair<Double, Double>> {
        val motion = GapMotion()
        val shown = mutableListOf<Pair<Double, Double>>()
        var next = 0
        var t = 0.0
        while (t <= until) {
            while (next < values.size && next <= t + 1e-9) {
                motion.receive(values[next], next.toDouble())
                next++
            }
            shown += t to motion.frame(t)
            t += frame
        }
        return shown
    }

    private fun maxStep(shown: List<Pair<Double, Double>>) =
        shown.zipWithNext { a, b -> abs(b.second - a.second) }.max()

    @Test
    fun `첫 값은 바로 보이고 같은 값만 오면 그대로다`() {
        val motion = GapMotion()
        motion.receive(-12.0, 0.0)
        assertEquals(-12.0, motion.frame(0.0))
        motion.receive(-12.0, 1.0)
        assertEquals(-12.0, motion.frame(1.5), 1e-9)
        assertTrue(motion.isSettled(3.0))
    }

    @Test
    fun `일정 속도면 1 m 계단 없이 매 프레임 조금씩 늘고 줄지 않는다`() {
        val shown = play((0..12).map { it * 1.0 })
        val warm = shown.filter { it.first >= 3.0 }
        assertTrue(maxStep(warm) < 0.1, "한 프레임에 0.1 m 넘게 뛰지 않는다: ${maxStep(warm)}")
        warm.zipWithNext { a, b -> assertTrue(b.second >= a.second - 1e-9, "되돌아가지 않는다 at ${b.first}") }
        // 값이 오는 순간에도 튀지 않는다.
        val atArrival = warm.zipWithNext().filter { (a, b) -> floor(a.first) != floor(b.first) }
        atArrival.forEach { (a, b) -> assertTrue(abs(b.second - a.second) < 0.1, "도착 때 점프 at ${b.first}") }
    }

    @Test
    fun `정수로 잘린 느린 속도(0점5 m 매초)도 계단 없이 실제 값 가까이 간다`() {
        val truth = { t: Double -> 0.5 * t }
        val shown = play((0..20).map { floor(truth(it.toDouble())) })
        val warm = shown.filter { it.first >= 4.0 }
        assertTrue(maxStep(warm) < 0.1, "계단: ${maxStep(warm)}")
        warm.forEach { (t, value) -> assertTrue(abs(value - truth(t)) < 1.5, "실제 값과 어긋남 at $t: $value") }
    }

    @Test
    fun `수신이 끊기면 1점5초까지만 예측하고 멈추며 어긋남은 3 m를 넘지 않는다`() {
        // 2 m/s로 오다가 5초 뒤 끊긴다.
        val shown = play((0..5).map { it * 2.0 }, until = 15.0)
        val last = 10.0
        shown.filter { it.first > 5.0 }.forEach { (t, value) ->
            assertTrue(value <= last + 3.0 + 1e-9, "어긋남 상한 at $t: $value")
        }
        val stopped = shown.filter { it.first >= 9.0 }.map { it.second }
        assertTrue(stopped.max() - stopped.min() < 1e-3, "끊긴 뒤에는 멈춘다")
    }

    @Test
    fun `상대 속도는 초속 4 m를 넘겨 예측하지 않는다`() {
        val motion = GapMotion()
        motion.receive(0.0, 0.0)
        motion.receive(9.0, 1.0)
        motion.receive(18.0, 2.0)
        var t = 2.0
        while (t < 6.0) {
            motion.frame(t)
            t += frame
        }
        assertTrue(motion.frame(6.0) <= 18.0 + 4.0 * 1.5 + 1e-9)
    }

    @Test
    fun `역전(앞서다 뒤처짐)도 0을 지나며 튀지 않는다`() {
        val shown = play((0..10).map { 5.0 - it })
        val warm = shown.filter { it.first >= 3.0 }
        assertTrue(maxStep(warm) < 0.1, "계단: ${maxStep(warm)}")
        warm.zipWithNext { a, b -> assertTrue(b.second <= a.second + 1e-9, "역전 중 되돌아가지 않는다 at ${b.first}") }
        assertTrue(warm.first().second > 0 && warm.last().second < 0)
    }

    @Test
    fun `멈춰 있다가(그릴 것 없음) 새 값이 와도 첫 프레임에 튀지 않는다(S15P31A207-335 2차)`() {
        val motion = GapMotion()
        motion.receive(0.0, 0.0)
        motion.frame(0.0)
        assertTrue(motion.isSettled(0.0))
        // 프레임 루프가 1초 쉬었다가 다음 값(3 m)을 받는다.
        motion.receive(3.0, 1.0)
        val step = motion.frame(1.0 + frame)
        assertTrue(step < 0.5, "쉰 시간만큼 한 번에 따라가지 않는다: $step")
    }

    @Test
    fun `두 번째 값으로 바로 상대 속도를 잡아 출발 직후에도 일정 속도로 따라간다(S15P31A207-335 2차)`() {
        val motion = GapMotion()
        motion.receive(0.0, 0.0)
        motion.receive(3.0, 1.0)
        var t = 1.0
        while (t < 1.9) {
            motion.frame(t)
            t += frame
        }
        // 3 m/s로 예측하면 1.9초의 예측값은 5.7 m이고 보이는 값은 약 0.25초 뒤처진 4.9 m 근처다(절반 속도면 4 m 아래).
        assertTrue(motion.frame(1.9) > 4.5, "두 번째 값부터 측정 속도를 쓴다: ${motion.frame(1.9)}")
    }

    @Test
    fun `출발 쪽 밖에서 들어오게 하면 시작 전에는 밖에 머물고 시작하면 되돌아가지 않고 제자리에 닿는다(S15P31A207-335 2차)`() {
        val motion = GapMotion()
        motion.enter(0.0, fromMeters = -18.0, atSeconds = 0.0)
        assertEquals(-18.0, motion.frame(0.3))
        assertTrue(motion.isSettled(0.3), "시작 전에는 그릴 것이 없다(준비를 기다린다)")
        motion.receive(0.0, 0.5)
        assertEquals(-18.0, motion.frame(0.6), "몸이 준비되기 전에는 값이 와도 밖에 머문다")

        motion.startEntry(0.7)
        assertTrue(!motion.isSettled(0.7), "들어오는 동안은 매 프레임 그린다")
        val shown = mutableListOf<Pair<Double, Double>>()
        var t = 0.7
        while (t <= 3.0) {
            shown += t to motion.frame(t)
            t += frame
        }
        shown.zipWithNext { a, b -> assertTrue(b.second >= a.second - 1e-9, "되돌아가지 않는다 at ${b.first}") }
        assertTrue(maxStep(shown) < 1.0, "한 프레임에 1 m 넘게 뛰지 않는다: ${maxStep(shown)}")
        assertEquals(0.0, shown.last().second, 1e-6)
        assertTrue(shown.first { it.second > -1e-6 }.first <= 0.7 + 1.0 + 1e-9, "1초 안에 제자리에 닿는다")
        assertTrue(motion.isSettled(3.0))
    }

    @Test
    fun `창 폭(30 m)보다 크게 바뀌면 따라가지 않고 바로 옮긴다`() {
        val motion = GapMotion()
        motion.receive(-5.0, 0.0)
        motion.frame(0.5)
        motion.receive(-80.0, 1.0)
        assertEquals(-80.0, motion.frame(1.0))
    }
}
