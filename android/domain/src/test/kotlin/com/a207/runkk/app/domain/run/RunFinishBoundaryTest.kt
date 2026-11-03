package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.testing.T0
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * R12 D2: 정지 중 종료 경계와 전송 직전 정지 구간 자르기. 서버는 endedAt = 시작 + 운동 시간 + 정지 합을 받아
 * 정지 구간이 [startedAt, endedAt] 안이고 from < to인지 본다(RunUpload, F.2.2). 기기 시계는 밀리초 아래 값을 가질 수 있고(마이크로초)
 * 보관은 epoch 밀리초로 자르므로, 잘린 뒤에도 정지 끝이 기록 끝을 넘지 않아야 한다.
 */
class RunFinishBoundaryTest {
    /** 시작에서 [millis]ms + [micros]µs 뒤. */
    private fun at(millis: Long, micros: Long = 0): Instant = T0.plusMillis(millis).plusNanos(micros * 1_000)

    private fun walked(start: Instant): RunSession = RunSession(freeSpec()).also { s ->
        s.start(start)
        s.onLocation(normal(1, 0.0))
        s.onLocation(normal(2, 5.0))
    }

    /** 보관함(epoch 밀리초)에 넣었다 꺼낸 값. PendingRunMapper와 같은 자르기다. */
    private fun CompletedRun.stored(): CompletedRun = copy(
        startedAt = Instant.ofEpochMilli(startedAt.toEpochMilli()),
        elapsed = Duration.ofMillis(elapsed.toMillis()),
        pauses = pauses.map {
            PauseInterval(Instant.ofEpochMilli(it.from.toEpochMilli()), Instant.ofEpochMilli(it.to.toEpochMilli()))
        }
    )

    private fun CompletedRun.assertPausesInsideRun() {
        pauses.forEach { p ->
            assertTrue(!p.from.isBefore(startedAt), "from $p ≥ start $startedAt")
            assertTrue(!p.to.isAfter(endedAt), "to $p ≤ end $endedAt")
            assertTrue(p.from.isBefore(p.to), "from < to $p")
        }
        pauses.zipWithNext().forEach { (a, b) -> assertTrue(a.to.isBefore(b.from), "시간순·맞닿지 않음 $a $b") }
    }

    @Test
    fun `정지 중 종료 - 밀리초 아래 시각이어도 마지막 정지 끝과 기록 끝이 같은 시각 하나다`() {
        val s = walked(at(0, micros = 900))
        s.pause(at(40_000, micros = 300))
        s.resume(at(60_000, micros = 700))
        s.pause(at(90_000, micros = 100))
        val run = assertIs<FinishOutcome.Saved>(s.finish(at(110_191, micros = 999))).run

        assertEquals(run.endedAt, run.pauses.last().to)
        assertEquals(at(110_191), run.endedAt)
        run.assertPausesInsideRun()
        // 보관함에 넣었다 꺼내도 값이 바뀌지 않는다(시각과 운동 시간이 모두 밀리초).
        val stored = run.stored()
        assertEquals(run, stored)
        assertEquals(stored.endedAt, stored.pauses.last().to)
    }

    @Test
    fun `정지 중 종료 - 다른 스레드가 더 늦은 시각으로 정지했으면 기록 끝을 그 시각으로 둔다`() {
        val s = walked(T0)
        s.pause(at(50_000))
        val run = assertIs<FinishOutcome.Saved>(s.finish(at(49_999))).run

        assertEquals(at(50_000), run.endedAt)
        assertEquals(emptyList(), run.pauses, "길이 0 정지는 남기지 않는다(서버는 from < to만 받는다)")
        assertEquals(Duration.ofMillis(50_000), run.elapsed)
    }

    @Test
    fun `같은 밀리초 안의 정지·재개는 정지 구간을 남기지 않는다`() {
        val s = walked(T0)
        s.pause(at(10_000, micros = 100))
        s.resume(at(10_000, micros = 900))
        val run = assertIs<FinishOutcome.Saved>(s.finish(at(20_000))).run

        assertEquals(emptyList(), run.pauses)
        assertEquals(Duration.ofSeconds(20), run.elapsed)
    }

    @Test
    fun `달리는 중 종료 - 기록 끝은 종료 시각(밀리초)이다`() {
        val s = walked(at(0, micros = 400))
        s.pause(at(5_000, micros = 600))
        s.resume(at(7_000, micros = 200))
        val run = assertIs<FinishOutcome.Saved>(s.finish(at(30_000, micros = 800))).run

        assertEquals(at(30_000), run.endedAt)
        assertEquals(Duration.ofMillis(28_000), run.elapsed)
        run.assertPausesInsideRun()
    }

    // ---------- 전송 직전 자르기(CompletedRun.withPausesInRange) ----------

    private fun record(elapsedMillis: Long, vararg pauses: Pair<Long, Long>) = CompletedRun(
        recordId = "rec-1",
        mode = RunMode.FREE,
        cardKind = CardKind.FREE_RUN,
        battleId = null,
        crewRunPostId = null,
        startedAt = T0,
        elapsed = Duration.ofMillis(elapsedMillis),
        distance = Distance(500),
        route = emptyList(),
        unstable = emptyList(),
        heartRates = emptyList(),
        pauses = pauses.map { (from, to) -> PauseInterval(at(from), at(to)) },
        savedAt = T0
    )

    @Test
    fun `옛 판이 남긴 1ms 넘친 기록 - 정지 끝을 기록 끝으로 자르고 기록 끝은 그대로 둔다(에뮬레이터 보관함 사례)`() {
        // 정지 합 20_000 + 20_101, 운동 시간 70_090 → 끝 = 시작 + 110_191, 둘째 정지 끝은 110_192.
        val overflowing = record(70_090, 40_000L to 60_000L, 90_091L to 110_192L)
        assertEquals(at(110_191), overflowing.endedAt)

        val fitted = overflowing.withPausesInRange()

        assertEquals(at(110_191), fitted.endedAt)
        assertEquals(PauseInterval(at(90_091), at(110_191)), fitted.pauses.last())
        assertEquals(Duration.ofMillis(70_091), fitted.elapsed)
        fitted.assertPausesInsideRun()
    }

    @Test
    fun `자르기 - 시작 앞 정지는 시작으로, 길이 0은 빼고, 겹치거나 맞닿은 구간은 합친다`() {
        val messy = record(
            50_000,
            -500L to 1_000L,
            10_000L to 10_000L,
            20_000L to 25_000L,
            25_000L to 27_000L,
            26_000L to 30_000L
        )
        val end = messy.endedAt

        val fitted = messy.withPausesInRange()

        assertEquals(listOf(PauseInterval(at(0), at(1_000)), PauseInterval(at(20_000), at(30_000))), fitted.pauses)
        assertEquals(end, fitted.endedAt)
        fitted.assertPausesInsideRun()
    }

    @Test
    fun `자르기 - 고칠 것이 없으면 같은 기록이다`() {
        val clean = record(60_000, 10_000L to 20_000L, 30_000L to 40_000L)
        assertSame(clean, clean.withPausesInRange())
        val noPauses = record(60_000)
        assertSame(noPauses, noPauses.withPausesInRange())
    }
}
