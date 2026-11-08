package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.testing.T0
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * RunSession 일시정지(A-02, D-93, D-121, D-144, D-256): 시험 문서 2.2.13 TC-6301~TC-6319, 2.2.16 TC-6363·TC-6366·TC-6367,
 * v0.14 TC-9623·TC-9624(잠금 상태 삭제, 상태도 2.4.7 v0.14). 잠금 관련 TC-6320·TC-6361·TC-6362·TC-6364·TC-6365·TC-6368·TC-6331은 폐기했다.
 * 시각 표기 "10:mm:ss"는 측정 시작(T0)을 10:00:00으로 본 값이다.
 */
class RunSessionPauseTest {
    /** 10:mm:ss(시작 10:00:00 = T0). */
    private fun t(minutes: Long, seconds: Long = 0): Instant = T0.plusSeconds(minutes * 60 + seconds)

    private fun session(mode: RunMode = RunMode.FREE): RunSession = RunSession(
        RunStartSpec(mode = mode, cardKind = CardKind.FREE_RUN, recordId = "rec-1")
    ).also { it.start(T0) }

    /**
     * 정상 포인트를 [from]초부터 [count]개, [step]m씩 놓는다. 시작 거리는 [startMeters]. 포인트 간격은 초속 5m(18km/h)로 [step]을 가는
     * 시간(최소 1초)이다: 이웃 포인트 사이가 25km/h를 넘으면 거리에서 빠진다(FR-047 v0.10).
     */
    private fun RunSession.walk(from: Long, count: Int, startMeters: Double, step: Double) {
        val interval = maxOf(1L, ceil(step / WALK_MPS).toLong())
        for (i in 0 until count) onLocation(normal(from + i * interval, startMeters + i * step))
    }

    private companion object {
        const val WALK_MPS = 5.0
    }

    // ---------- pause / resume (TC-6301~TC-6305) ----------

    @Test
    fun `TC-6301 자유런 RUNNING에서 pause하면 PAUSED이고 열린 정지 구간이 생긴다`() {
        val s = session()
        assertTrue(s.pause(t(10)))
        assertEquals(RunSession.State.PAUSED, s.state)
        assertTrue(s.isPaused)
        assertEquals(Duration.ofSeconds(30), s.currentPause(t(10, 30)))
        assertEquals(emptyList(), s.pauses)
    }

    @Test
    fun `TC-6302 대결은 pause가 거짓이고 상태가 그대로다`() {
        for (mode in listOf(RunMode.BATTLE)) {
            val s = session(mode)
            assertFalse(s.pause(t(10)), "$mode")
            assertEquals(RunSession.State.RUNNING, s.state)
            assertNull(s.currentPause(t(10, 30)))
            assertEquals(emptyList(), s.pauses)
        }
    }

    @Test
    fun `TC-6303 RUNNING이 아니면 pause는 거짓이고 열린 정지 시작을 덮어쓰지 않는다`() {
        val paused = session().also { it.pause(t(10)) }
        val countdown = RunSession(freeSpec())
        val finished = session().also { it.end(t(5)) }
        listOf(paused, countdown, finished).forEach { s ->
            val before = s.state
            assertFalse(s.pause(t(12)))
            assertEquals(before, s.state)
        }
        assertEquals(Duration.ofMinutes(2), paused.currentPause(t(12)))
    }

    @Test
    fun `TC-6304 PAUSED에서 resume하면 정지 구간을 닫고 RUNNING으로 간다`() {
        val s = session().also { it.pause(t(10)) }
        assertTrue(s.resume(t(15)))
        assertEquals(RunSession.State.RUNNING, s.state)
        assertEquals(listOf(PauseInterval(t(10), t(15))), s.pauses)
        assertNull(s.currentPause(t(16)))
        assertEquals(t(15), s.lastResumedAt)
    }

    @Test
    fun `TC-6305 PAUSED가 아니면 resume은 거짓이다`() {
        val running = session()
        val countdown = RunSession(freeSpec())
        val finished = session().also { it.end(t(5)) }
        listOf(running, countdown, finished).forEach { s ->
            val before = s.state
            assertFalse(s.resume(t(15)))
            assertEquals(before, s.state)
            assertEquals(emptyList(), s.pauses)
        }
    }

    // ---------- tick (TC-6306~TC-6313) ----------

    @Test
    fun `TC-6306 정지가 없으면 경과는 벽시계이고 현재·평균 페이스를 계산한다`() {
        val s = session()
        s.onLocation(normal(19 * 60 + 30, 1_900.0))
        s.onLocation(normal(20 * 60, 2_000.0))
        val p = s.tick(t(20))
        assertEquals(Duration.ofSeconds(1_200), p.elapsed)
        assertEquals(Distance(100), p.distance)
        assertEquals(Pace(300), p.currentPace)
        assertFalse(p.isPaused)
    }

    @Test
    fun `TC-6307 평균 페이스는 운동 시간 나누기 거리다`() {
        val s = session()
        s.walk(from = 1, count = 2, startMeters = 0.0, step = 0.0)
        s.pause(t(5))
        s.resume(t(10))
        s.onLocation(normal(10 * 60 + 1, 0.0))
        s.onLocation(normal(10 * 60 + 301, 1_500.0))
        val p = s.tick(t(20))
        assertEquals(Duration.ofSeconds(900), p.elapsed)
        assertEquals(Distance(1_500), p.distance)
        assertEquals(Pace(600), p.averagePace)
    }

    @Test
    fun `TC-6308 정지 중 tick은 지표를 고정하고 현재 페이스는 빈 값, 심박은 갱신한다`() {
        val s = session()
        s.onLocation(normal(9 * 60 + 30, 0.0))
        s.onLocation(normal(10 * 60, 200.0))
        s.onHeartRate(HeartRateSample(t(9, 59), 150))
        assertEquals(Pace(150), s.tick(t(10)).currentPace)
        s.pause(t(10))
        s.onHeartRate(HeartRateSample(t(11), 160))
        for (now in listOf(t(12), t(14))) {
            val p = s.tick(now)
            assertEquals(Duration.ofSeconds(600), p.elapsed)
            assertEquals(Distance(200), p.distance)
            assertEquals(Pace(3_000), p.averagePace)
            assertNull(p.currentPace)
            assertEquals(160, p.heartRate?.bpm)
            assertTrue(p.isPaused)
        }
    }

    @Test
    fun `TC-6309 창 안 거리 차가 없으면 직전 현재 페이스를 쓴다`() {
        val s = session()
        s.onLocation(normal(19 * 60, 1_810.0))
        s.onLocation(normal(19 * 60 + 29, 1_900.0))
        val before = s.tick(t(19, 29)).currentPace
        s.onLocation(normal(20 * 60, 1_900.0))
        val p = s.tick(t(20))
        assertEquals(before, p.currentPace)
        assertEquals(Pace.of(Duration.ofSeconds(1_200), Distance(90)), p.averagePace)
    }

    @Test
    fun `TC-6310 재개 직후 현재 페이스는 새 구간 표본이 찰 때까지 빈 값이다`() {
        val s = session()
        s.walk(from = 9 * 60 + 30, count = 31, startMeters = 0.0, step = 3.0)
        s.tick(t(10))
        s.pause(t(10))
        assertNull(s.tick(t(12)).currentPace)
        s.resume(t(15))
        s.onLocation(normal(15 * 60 + 1, 500.0))
        assertNull(s.tick(t(15, 1)).currentPace)
        s.onLocation(normal(15 * 60 + 11, 530.0))
        assertEquals(Pace(333), s.tick(t(15, 11)).currentPace)
    }

    @Test
    fun `TC-6311 포인트가 없으면 평균 페이스는 null이다`() {
        val p = session().tick(t(0, 5))
        assertEquals(Duration.ofSeconds(5), p.elapsed)
        assertEquals(Distance.ZERO, p.distance)
        assertNull(p.averagePace)
    }

    @Test
    fun `TC-6312 닫힌 정지 2개·100개를 모두 뺀다`() {
        val two = session()
        two.pause(t(5)).also { two.resume(t(10)) }
        two.pause(t(20)).also { two.resume(t(30)) }
        assertEquals(Duration.ofSeconds(2_700), two.tick(t(60)).elapsed)

        val hundred = session()
        for (i in 0 until 100L) {
            hundred.pause(t(0, 20 * i + 1))
            hundred.resume(t(0, 20 * i + 11))
        }
        assertEquals(100, hundred.pauses.size)
        assertEquals(Duration.ofSeconds(2_600), hundred.tick(t(60)).elapsed)
    }

    @Test
    fun `TC-6313 pausedTotal은 닫힌 정지와 열린 정지를 더한다`() {
        val s = RunSession(freeSpec()).also { it.start(t(-10)) }
        s.pause(t(0))
        s.resume(t(5))
        s.pause(t(10))
        assertEquals(Duration.ofMinutes(8), s.pausedTotal(t(13)))
        assertEquals(Duration.ofMinutes(3), s.currentPause(t(13)))
        assertEquals(Duration.ofSeconds(900), s.tick(t(13)).elapsed)
    }

    // ---------- onLocation (TC-6314~TC-6319) ----------

    @Test
    fun `TC-6314 정지 중 포인트는 버린다`() {
        val s = session()
        s.walk(from = 1, count = 2, startMeters = 0.0, step = 1_000.0)
        s.pause(t(10))
        s.onLocation(normal(11 * 60, 1_000.0))
        s.onLocation(normal(11 * 60 + 1, 1_005.0))
        s.onLocation(unstable(11 * 60 + 2, 1_010.0))
        assertEquals(Distance(1_000), s.tick(t(12)).distance)
        val run = assertIs<FinishOutcome.Saved>(s.end(t(12))).run
        assertEquals(2, run.route.size)
        assertEquals(emptyList(), run.unstable)
    }

    @Test
    fun `TC-6315 재개 뒤 첫 정상 포인트는 정지 직전 포인트와의 거리를 더하지 않는다`() {
        val s = session()
        s.walk(from = 1, count = 2, startMeters = 0.0, step = 1_000.0)
        s.pause(t(10))
        s.resume(t(15))
        s.onLocation(normal(15 * 60 + 1, 1_300.0))
        assertEquals(Distance(1_000), s.tick(t(15, 1)).distance)
        s.onLocation(normal(15 * 60 + 2, 1_303.0))
        assertEquals(Distance(1_003), s.tick(t(15, 2)).distance)
    }

    @Test
    fun `TC-6316 재개 뒤 첫 포인트가 불안정이면 구간은 마지막 재개 시각에서 시작하고 건너뛴 거리는 0이다`() {
        val s = session()
        s.walk(from = 1, count = 2, startMeters = 0.0, step = 1_000.0)
        s.pause(t(10))
        s.resume(t(15))
        s.onLocation(unstable(15 * 60 + 12, 1_300.0))
        val run = assertIs<FinishOutcome.Saved>(s.end(t(16))).run
        assertEquals(listOf(UnstableSegment(t(15), t(15, 12), Distance.ZERO)), run.unstable)
        assertEquals(Distance(1_000), run.distance)
    }

    @Test
    fun `TC-6317 재개한 적 없이 첫 포인트가 불안정이면 구간은 측정 시작 시각에서 시작한다`() {
        val s = session()
        s.onLocation(unstable(12, 50.0))
        s.onLocation(normal(13, 50.0))
        s.onLocation(normal(14, 55.0))
        val run = assertIs<FinishOutcome.Saved>(s.end(t(1))).run
        assertEquals(UnstableSegment(t(0), t(0, 12), Distance.ZERO), run.unstable.first())
    }

    @Test
    fun `TC-6318 두 번 재개하면 가장 최근 재개 시각을 쓴다`() {
        val s = session()
        s.walk(from = 1, count = 2, startMeters = 0.0, step = 100.0)
        s.pause(t(10))
        s.resume(t(15))
        s.walk(from = 15 * 60 + 1, count = 2, startMeters = 200.0, step = 10.0)
        s.pause(t(25))
        s.resume(t(30))
        s.onLocation(unstable(30 * 60 + 5, 300.0))
        val run = assertIs<FinishOutcome.Saved>(s.end(t(31))).run
        assertEquals(t(30), run.unstable.single().startAt)
    }

    @Test
    fun `TC-6319 정지 중 심박은 기록에 남는다`() {
        val s = session()
        s.walk(from = 1, count = 2, startMeters = 0.0, step = 1_000.0)
        s.pause(t(10))
        s.onHeartRate(HeartRateSample(t(12), 150))
        s.resume(t(15))
        val run = assertIs<FinishOutcome.Saved>(s.finish(t(20))).run
        assertTrue(HeartRateSample(t(12), 150) in run.heartRates)
    }

    // ---------- 상태 전이 (TC-6363·TC-6366·TC-6367, TC-9623·TC-9624) ----------

    @Test
    fun `TC-6363 PAUSED에서 finish하면 마지막 정지를 종료 시각에 닫고 운동 시간으로 저장한다`() {
        val s = session()
        s.walk(from = 1, count = 2, startMeters = 0.0, step = 1_000.0)
        s.pause(t(10))
        val run = assertIs<FinishOutcome.Saved>(s.finish(t(12))).run
        assertEquals(listOf(PauseInterval(t(10), t(12))), run.pauses)
        assertEquals(Duration.ofSeconds(600), run.elapsed)
        assertEquals(Distance(1_000), run.distance)
        assertEquals(RunSession.State.FINISHED, s.state)
        assertNull(s.currentPause(t(12)))
    }

    @Test
    fun `TC-6366 정지 두 번 뒤 종료하면 정지 구간이 시간순이고 겹치지 않는다`() {
        val s = session()
        s.walk(from = 1, count = 2, startMeters = 0.0, step = 1_000.0)
        s.pause(t(5)).also { s.resume(t(7)) }
        s.pause(t(15)).also { s.resume(t(20)) }
        val run = assertIs<FinishOutcome.Saved>(s.finish(t(30))).run
        assertEquals(listOf(PauseInterval(t(5), t(7)), PauseInterval(t(15), t(20))), run.pauses)
        assertTrue(run.pauses.all { it.from < it.to })
        assertTrue(run.pauses.zipWithNext().all { (a, b) -> a.to <= b.from })
        assertEquals(Duration.ofSeconds(1_380), run.elapsed)
    }

    @Test
    fun `TC-6367 대결 기록의 정지 구간은 빈 목록이다`() {
        val s = session(RunMode.BATTLE)
        s.walk(from = 1, count = 2, startMeters = 0.0, step = 5_000.0)
        val run = assertIs<FinishOutcome.Saved>(s.end(t(30))).run
        assertEquals(emptyList(), run.pauses)
    }

    @Test
    fun `TC-9623 RunSession 상태는 COUNTDOWN·RUNNING·PAUSED·FINISHED 넷이고 잠금 연산이 없다`() {
        assertEquals(
            listOf("COUNTDOWN", "RUNNING", "PAUSED", "FINISHED"),
            RunSession.State.entries.map { it.name }
        )
        val names = RunSession::class.java.declaredMethods.map { it.name }.toSet()
        val removed = setOf(
            "lock",
            "unlock",
            "tryLock",
            "tryUnlock",
            "hintUnlock",
            "unlockHintIfLocked",
            "isLocked",
            "pauseFromWatch",
            "resumeFromWatch"
        )
        assertEquals(emptySet(), names intersect removed)
    }

    @Test
    fun `TC-9624 상태도 2_4_7 v0_14에 없는 전이는 거부하고 상태가 그대로다`() {
        val countdown = RunSession(freeSpec())
        assertFalse(countdown.pause(t(1)))
        assertEquals(RunSession.State.COUNTDOWN, countdown.state)

        val finished = session().also { it.end(t(5)) }
        assertFalse(finished.pause(t(6)))
        assertFalse(finished.resume(t(6)))
        assertFailsWith<IllegalStateException> { finished.finish(t(6)) }
        assertEquals(RunSession.State.FINISHED, finished.state)

        val running = session()
        assertFalse(running.resume(t(6)))
        assertEquals(RunSession.State.RUNNING, running.state)

        // 허용: COUNTDOWN → RUNNING ⇄ PAUSED → FINISHED
        val allowed = RunSession(freeSpec())
        allowed.start(T0)
        assertTrue(allowed.pause(t(1)))
        assertTrue(allowed.resume(t(2)))
        assertTrue(allowed.pause(t(3)))
        allowed.finish(t(4))
        assertEquals(RunSession.State.FINISHED, allowed.state)
    }

    @Test
    fun `isPausable은 자유런과 크루런만 참이다 (D-137, S15P31A207-236)`() {
        assertEquals(listOf(true, false, true), RunMode.entries.map { it.isPausable })
    }

    @Test
    fun `크루런은 각자 일시정지한다 (S15P31A207-236)`() {
        val s = session(RunMode.CREW_RUN)
        assertTrue(s.pause(t(10)))
        assertEquals(RunSession.State.PAUSED, s.state)
    }
}
