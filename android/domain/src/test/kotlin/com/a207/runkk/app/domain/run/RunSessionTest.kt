package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.SignalState
import com.a207.runkk.app.domain.testing.T0
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RunSession(A-02, D-36, D-49): 거리 누적·불안정 제외·현재 페이스 30초·누름 종류별 연산. */
class RunSessionTest {
    private fun running(): RunSession = RunSession(freeSpec()).also { it.start(T0) }

    private fun RunSession.runFor(seconds: Long, metersPerSecond: Double = 3.0) {
        for (t in 1..seconds) onLocation(normal(t, t * metersPerSecond))
    }

    @Test
    fun `시작하면 경과 0, 거리 0`() {
        val session = RunSession(freeSpec())
        assertEquals(RunSession.State.COUNTDOWN, session.state)
        session.start(T0)
        val p = session.tick(T0)
        assertEquals(RunSession.State.RUNNING, session.state)
        assertEquals(Duration.ZERO, p.elapsed)
        assertEquals(Distance.ZERO, p.distance)
    }

    @Test
    fun `정상 포인트 사이 거리를 누적한다`() {
        val session = running()
        session.runFor(10)
        assertEquals(Distance(27), session.tick(at(10)).distance)
    }

    @Test
    fun `불안정 포인트는 거리를 더하지 않고 불안정 구간으로 남긴다`() {
        val session = running()
        session.onLocation(normal(1, 0.0))
        session.onLocation(normal(2, 3.0))
        session.onLocation(unstable(20, 100.0))
        session.onLocation(normal(21, 103.0))
        assertEquals(Distance(6), session.tick(at(21)).distance)
        val run = assertIs<FinishOutcome.Saved>(session.finish(at(21))).run
        assertEquals(listOf(UnstableSegment(at(2), at(20), Distance(97))), run.unstable)
        assertEquals(4, run.route.size)
    }

    @Test
    fun `순간이동처럼 시속 25km를 넘는 한 구간은 거리에 더하지 않는다(FR-047 v0_10)`() {
        val session = running()
        session.onLocation(normal(1, 0.0))
        session.onLocation(normal(2, 3.0))
        // 1초에 9,078km(남쪽으로) 순간이동
        session.onLocation(normal(3, -9_078_000.0))
        session.onLocation(normal(4, -9_077_997.0))
        assertEquals(Distance(6), session.tick(at(4)).distance)
        val run = assertIs<FinishOutcome.Saved>(session.finish(at(4))).run
        assertEquals(Distance(6), run.distance)
        assertEquals(4, run.route.size)
    }

    @Test
    fun `차를 탄 것처럼 긴 초과 구간도 빼고, 다시 달리면 이어서 센다`() {
        val session = running()
        session.runFor(10)
        val carMps = 40 / 3.6
        for (t in 11L..70L) session.onLocation(normal(t, 30.0 + (t - 10) * carMps))
        val carEnd = 30.0 + 60 * carMps
        for (t in 71L..80L) session.onLocation(normal(t, carEnd + (t - 70) * 3.0))
        assertEquals(Distance(57), session.tick(at(80)).distance)
    }

    @Test
    fun `시속 25km 아래 구간은 그대로 더한다`() {
        val session = running()
        session.onLocation(normal(0, 0.0))
        session.runFor(10, metersPerSecond = 24.9 / 3.6)
        assertEquals(Distance(69), session.tick(at(10)).distance)
    }

    @Test
    fun `현재 페이스는 최근 30초, 평균 페이스는 경과 나누기 거리`() {
        val session = running()
        session.runFor(60)
        val p = session.tick(at(60))
        assertEquals(Pace(333), p.currentPace)
        assertEquals(Pace.of(Duration.ofSeconds(60), Distance(177)), p.averagePace)
        assertEquals(SignalState.NORMAL, p.signal)
    }

    @Test
    fun `종료 짧게 누름은 상태를 바꾸지 않고 안내만 돌려준다`() {
        val session = running()
        val guidance = session.hintFinish()
        assertEquals(Guidance.Kind.FINISH_HINT, guidance.kind)
        assertEquals(RunSession.State.RUNNING, session.state)
    }

    @Test
    fun `잠금 중 finish는 거부한다`() {
        val session = running()
        session.runFor(5)
        session.lock()
        assertEquals(FinishOutcome.Rejected, session.finish(at(5)))
        assertEquals(RunSession.State.LOCKED, session.state)
        assertTrue(session.tick(at(5)).isLocked)
    }

    @Test
    fun `end는 잠금과 무관하게 확정한다`() {
        val session = running()
        session.runFor(5)
        session.lock()
        val run = assertIs<FinishOutcome.Saved>(session.end(at(5))).run
        assertEquals(Distance(12), run.distance)
        assertEquals(Duration.ofSeconds(5), run.elapsed)
        assertEquals("rec-1", run.recordId)
        assertEquals(RunSession.State.FINISHED, session.state)
    }

    @Test
    fun `거리 0이면 저장하지 않는다`() {
        val session = running()
        assertEquals(FinishOutcome.NotSaved, session.finish(at(3)))
        assertEquals(RunSession.State.FINISHED, session.state)
    }

    @Test
    fun `해제 짧게 누름은 상태 불변, 길게 누름은 해제`() {
        val session = running()
        session.lock()
        assertEquals(Guidance.Kind.UNLOCK_HINT, session.hintUnlock().kind)
        assertEquals(RunSession.State.LOCKED, session.state)
        session.unlock()
        assertEquals(RunSession.State.RUNNING, session.state)
    }

    @Test
    fun `금지 전이는 IllegalStateException`() {
        val session = running()
        assertFailsWith<IllegalStateException> { session.unlock() }
        session.finish(at(1))
        assertFailsWith<IllegalStateException> { session.lock() }
        assertFailsWith<IllegalStateException> { session.end(at(2)) }
    }

    @Test
    fun `격차와 순위를 반영한다`() {
        val session = running()
        assertNull(session.gapMeters)
        session.updateGap(-12)
        session.updateRank(CrewRank(2, 5))
        assertEquals(-12, session.gapMeters)
        assertEquals(CrewRank(2, 5), session.rank)
    }

    @Test
    fun `목표 거리 처음 도달 시각을 보간한다`() {
        val session = running()
        session.runFor(5)
        assertEquals(at(4).plusMillis(333), session.arrivalAt(Distance(10)))
        assertNull(session.arrivalAt(Distance(100)))
    }

    @Test
    fun `측정 중 심박은 기록에 담기고 30초 안의 최근 값이 진행 지표에 보인다`() {
        val session = running()
        session.runFor(2)
        session.onHeartRate(HeartRateSample(at(2), 150))
        assertEquals(150, session.tick(at(10)).heartRate?.bpm)
        assertNull(session.tick(at(40)).heartRate)
        val run = assertIs<FinishOutcome.Saved>(session.finish(at(40))).run
        assertEquals(listOf(HeartRateSample(at(2), 150)), run.heartRates)
    }

    @Test
    fun `S-19 창의 처음과 끝 시각이 같으면 현재 페이스를 만들지 않는다`() {
        val session = running()
        session.onLocation(normal(1, 0.0))
        session.onLocation(FilteredPoint(at(1), north(5.0), SignalState.NORMAL))
        assertNull(session.tick(at(1)).currentPace)
    }
}
