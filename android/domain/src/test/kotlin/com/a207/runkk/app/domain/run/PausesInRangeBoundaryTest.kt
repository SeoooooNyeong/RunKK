package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.testing.T0
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * CompletedRun.withPausesInRange 경곗값 보강(test 16.12.2 우선 8, FR-247, R12 D2): 정지 시작·끝 ±1ms, 두 정지 간격 0·1ms,
 * 순서가 뒤섞인 목록, 다른 구간을 품는 구간, 기록 끝 뒤에만 있는 정지, 기록 끝이 시작보다 앞인 방어 경로.
 * 기록 끝 = 시작 + 운동 시간 + 저장된 정지 합이다.
 */
class PausesInRangeBoundaryTest {
    @Test
    fun `TC-5447 정지 시작이 기록 시작과 같으면 그대로 둔다`() {
        val run = run(Duration.ofSeconds(60), pause(0, 5_000))

        assertSame(run, run.withPausesInRange())
    }

    @Test
    fun `TC-5447 정지 시작이 기록 시작보다 1ms 앞이면 기록 시작으로 자르고 운동 시간을 1ms 늘린다`() {
        val fitted = run(Duration.ofSeconds(60), pause(-1, 5_000)).withPausesInRange()

        assertEquals(listOf(pause(0, 5_000)), fitted.pauses)
        assertEquals(Duration.ofMillis(60_001), fitted.elapsed)
    }

    @Test
    fun `TC-5447 정지 끝이 기록 끝과 같거나 1ms 앞이면 그대로 둔다`() {
        val atEnd = run(Duration.ofSeconds(60), pause(60_000, 65_000))
        val beforeEnd = run(Duration.ofSeconds(60), pause(59_999, 64_999))

        assertEquals(at(65_000), atEnd.endedAt)
        assertSame(atEnd, atEnd.withPausesInRange())
        assertSame(beforeEnd, beforeEnd.withPausesInRange())
    }

    @Test
    fun `TC-5447 두 정지의 간격이 0ms면 하나로 합치고 1ms면 둘로 둔다`() {
        val touching = run(Duration.ofSeconds(60), pause(10_000, 20_000), pause(20_000, 30_000)).withPausesInRange()
        val apart = run(Duration.ofSeconds(60), pause(10_000, 20_000), pause(20_001, 30_000))

        assertEquals(listOf(pause(10_000, 30_000)), touching.pauses)
        assertEquals(Duration.ofSeconds(60), touching.elapsed)
        assertSame(apart, apart.withPausesInRange())
    }

    @Test
    fun `TC-5448 시각 순서가 뒤섞인 정지 목록은 정렬한 것과 같은 결과다`() {
        val shuffled = run(Duration.ofSeconds(60), pause(30_000, 40_000), pause(10_000, 20_000)).withPausesInRange()

        assertEquals(listOf(pause(10_000, 20_000), pause(30_000, 40_000)), shuffled.pauses)
        assertEquals(Duration.ofSeconds(60), shuffled.elapsed)
    }

    @Test
    fun `TC-5448 다른 구간을 통째로 품는 구간은 바깥 구간 하나로 남는다`() {
        val fitted = run(Duration.ofSeconds(60), pause(10_000, 50_000), pause(20_000, 30_000)).withPausesInRange()

        assertEquals(listOf(pause(10_000, 50_000)), fitted.pauses)
        // 기록 끝(60 + 40 + 10초)은 그대로 두고 운동 시간 = 끝 − 시작 − 남은 정지(40초)다.
        assertEquals(Duration.ofSeconds(70), fitted.elapsed)
    }

    @Test
    fun `TC-5448 기록 끝 뒤에만 있는 정지는 빼고 운동 시간은 끝 − 시작 − 나머지 정지다`() {
        val fitted = run(Duration.ofSeconds(60), pause(200_000, 210_000)).withPausesInRange()

        assertEquals(emptyList(), fitted.pauses)
        assertEquals(Duration.ofSeconds(70), fitted.elapsed)
    }

    @Test
    fun `TC-5532 기록 끝이 시작보다 앞이면 정지가 모두 빠지고 운동 시간은 0이다(음수로 두지 않는다)`() {
        val fitted = run(Duration.ofSeconds(-10), pause(0, 5_000)).withPausesInRange()

        assertEquals(emptyList(), fitted.pauses)
        assertEquals(Duration.ZERO, fitted.elapsed)
    }

    private fun at(millis: Long): Instant = T0.plusMillis(millis)

    private fun pause(from: Long, to: Long) = PauseInterval(at(from), at(to))

    private fun run(elapsed: Duration, vararg pauses: PauseInterval) = CompletedRun(
        recordId = "r-1",
        mode = RunMode.FREE,
        cardKind = CardKind.FREE_RUN,
        battleId = null,
        crewRunPostId = null,
        startedAt = T0,
        elapsed = elapsed,
        distance = Distance(2_000),
        route = emptyList(),
        unstable = emptyList(),
        heartRates = emptyList(),
        pauses = pauses.toList(),
        savedAt = T0
    )
}
