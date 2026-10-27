package com.a207.runkk.watch.alert

import com.a207.runkk.watch.testing.FakeAlertOutput
import com.a207.runkk.watch.testing.SchedulerMonotonicClock
import com.a207.runkk.watch.testing.T0
import com.a207.runkk.wear.contract.LeadChangeMessage
import com.a207.runkk.wear.contract.ProximityMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * 6.1.15 ProximityAlertPlayer(FR-196, FR-188, D-96, D-158): 폰이 준 간격대로 비프음·진동을 반복하고,
 * 끔·3초 만료·러닝 끝에 멈추며, 추월·역전은 1초 긴 진동을 먼저 낸다. 가짜 진동기·비프음과 가상 시계로 본다(TC-6561~6567).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProximityAlertPlayerTest {
    private class Harness(val player: ProximityAlertPlayer, val output: FakeAlertOutput)

    private fun TestScope.harness(): Harness {
        val clock = SchedulerMonotonicClock(testScheduler)
        val output = FakeAlertOutput(clock)
        return Harness(ProximityAlertPlayer(output, output, clock, backgroundScope), output)
    }

    private fun TestScope.active(intervalMillis: Long, gapMeters: Int = 50) = ProximityMessage(
        isActive = true,
        gapMeters = gapMeters,
        intervalMillis = intervalMillis,
        isAhead = false,
        sentAtEpochMillis = T0 + testScheduler.currentTime
    )

    private fun TestScope.inactive() = ProximityMessage(
        isActive = false,
        gapMeters = 0,
        intervalMillis = 0,
        isAhead = false,
        sentAtEpochMillis = T0 + testScheduler.currentTime
    )

    private fun TestScope.lead(kind: LeadChangeMessage.Kind) = LeadChangeMessage(kind, T0 + testScheduler.currentTime)

    private fun TestScope.advance(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `TC-6561 새 메시지가 없으면 간격대로 울리다가 3초에 스스로 멈춘다`() = runTest {
        val h = harness()

        h.player.onProximity(active(intervalMillis = 1_000))
        runCurrent()
        advance(10_000)

        assertEquals(listOf(0L, 1_000L, 2_000L), h.output.beepTimes())
        // 비프마다 짧은 진동도 함께 낸다.
        assertEquals(
            listOf(0L, 1_000L, 2_000L),
            h.output.events.filter { it.kind == FakeAlertOutput.Kind.VIBRATE }.map { it.atMillis }
        )
        assertFalse(h.player.isPlaying.value)
    }

    @Test
    fun `TC-6562 새 메시지가 오면 폰이 준 간격으로 바꾼다`() = runTest {
        val h = harness()
        h.player.onProximity(active(intervalMillis = 1_000))
        runCurrent()
        advance(1_500)

        h.player.onProximity(active(intervalMillis = 500))
        runCurrent()
        advance(1_600)

        assertEquals(listOf(0L, 1_000L, 1_500L, 2_000L, 2_500L, 3_000L), h.output.beepTimes())
    }

    @Test
    fun `간격이 길어지면 마지막 비프에서 새 간격만큼 기다린다`() = runTest {
        val h = harness()
        h.player.onProximity(active(intervalMillis = 500))
        runCurrent()
        advance(600)

        h.player.onProximity(active(intervalMillis = 1_500))
        runCurrent()
        advance(2_000)

        assertEquals(listOf(0L, 500L, 2_000L), h.output.beepTimes())
    }

    @Test
    fun `TC-6563 끔 메시지가 오면 바로 멈추고 더 울리지 않는다`() = runTest {
        val h = harness()
        h.player.onProximity(active(intervalMillis = 300))
        runCurrent()
        advance(350)

        h.player.onProximity(inactive())
        runCurrent()
        val before = h.output.events.size
        advance(5_000)

        assertFalse(h.player.isPlaying.value)
        assertEquals(before, h.output.events.size)
        assertEquals(listOf(0L, 300L), h.output.beepTimes())
    }

    @Test
    fun `TC-6564 마지막 메시지 뒤 3초 정각부터 멈춘다`() = runTest {
        listOf(2_900L to true, 3_000L to false, 3_100L to false).forEach { (after, expectedPlaying) ->
            val h = harness()
            h.player.onProximity(active(intervalMillis = 300))
            runCurrent()

            advance(after)

            assertEquals(expectedPlaying, h.player.isPlaying.value, "마지막 메시지 뒤 ${after}ms")
            h.player.stop()
        }
    }

    @Test
    fun `유지 신호가 2초마다 오면 계속 울린다`() = runTest {
        val h = harness()
        h.player.onProximity(active(intervalMillis = 1_000))
        runCurrent()

        repeat(5) {
            advance(2_000)
            h.player.onProximity(active(intervalMillis = 1_000))
            runCurrent()
        }

        assertTrue(h.player.isPlaying.value)
        assertEquals((0L..10_000L step 1_000L).toList(), h.output.beepTimes())
    }

    @Test
    fun `TC-6565 러닝이 끝나면 멈춘다`() = runTest {
        val h = harness()
        h.player.onProximity(active(intervalMillis = 300))
        runCurrent()

        h.player.stop()
        runCurrent()
        val before = h.output.events.size
        advance(2_000)

        assertFalse(h.player.isPlaying.value)
        assertEquals(before, h.output.events.size)
    }

    @Test
    fun `TC-6566 반복 중 추월이 오면 1초 긴 진동을 먼저 내고 끝난 뒤 같은 간격으로 반복을 잇는다`() = runTest {
        val h = harness()
        h.player.onProximity(active(intervalMillis = 300))
        runCurrent()
        advance(1_000)

        h.player.onLeadChange(lead(LeadChangeMessage.Kind.OVERTOOK))
        runCurrent()
        advance(1_000)
        h.player.onProximity(active(intervalMillis = 300))
        runCurrent()
        advance(700)

        val long = h.output.events.filter { it.durationMillis == ProximityAlertPlayer.LEAD_CHANGE_VIBRATION_MILLIS }
        assertEquals(listOf(1_000L), long.map { it.atMillis })
        val afterLead = h.output.events.drop(h.output.events.indexOf(long.single()) + 1)
        assertEquals(FakeAlertOutput.Kind.VIBRATE, long.single().kind)
        // 긴 진동 동안(1,000~2,000ms)은 비프가 없고, 끝난 시각부터 300ms 간격으로 다시 울린다.
        assertEquals(
            listOf(2_000L, 2_300L, 2_600L),
            afterLead.filter { it.kind == FakeAlertOutput.Kind.BEEP }.map { it.atMillis }
        )
        assertEquals(listOf(0L, 300L, 600L, 900L), h.output.beepTimes().filter { it < 1_000L })
    }

    @Test
    fun `TC-6567 반복이 없을 때 역전이 오면 1초 긴 진동 한 번만 내고 반복을 시작하지 않는다`() = runTest {
        val h = harness()

        h.player.onLeadChange(lead(LeadChangeMessage.Kind.OVERTAKEN))
        runCurrent()
        advance(5_000)

        assertEquals(
            listOf(
                FakeAlertOutput.Event(
                    0,
                    FakeAlertOutput.Kind.VIBRATE,
                    ProximityAlertPlayer.LEAD_CHANGE_VIBRATION_MILLIS
                )
            ),
            h.output.events
        )
        assertFalse(h.player.isPlaying.value)
    }

    @Test
    fun `간격은 계산하지 않고 그대로 쓰되 0 이하 같은 잘못된 값으로 쉬지 않고 울리지는 않는다`() = runTest {
        val h = harness()

        h.player.onProximity(active(intervalMillis = 0))
        runCurrent()
        advance(1_000)

        assertTrue(h.output.beepTimes().size <= (1_000 / ProximityAlertPlayer.MIN_INTERVAL_MILLIS + 1).toInt())
    }

    @Test
    fun `진동기·비프음이 예외를 던져도 반복은 이어지고 추월 진동은 부르는 쪽으로 예외를 넘기지 않는다`() = runTest {
        val h = harness()
        h.output.isFailing = true

        h.player.onLeadChange(lead(LeadChangeMessage.Kind.OVERTOOK))
        h.player.onProximity(active(intervalMillis = 500))
        runCurrent()
        advance(1_100)

        assertTrue(h.player.isPlaying.value)
        assertEquals(listOf(1_000L), h.output.beepTimes())
    }

    @Test
    fun `아주 큰 간격도 넘침 없이 한 번 울리고 만료에 멈춘다`() = runTest {
        val h = harness()

        h.player.onProximity(active(intervalMillis = Long.MAX_VALUE))
        runCurrent()
        advance(10_000)

        assertEquals(listOf(0L), h.output.beepTimes())
        assertFalse(h.player.isPlaying.value)
    }

    @Test
    fun `반복이 없을 때 역전 직후 근접 알림이 켜지면 첫 비프는 긴 진동이 끝난 뒤다`() = runTest {
        val h = harness()
        h.player.onLeadChange(lead(LeadChangeMessage.Kind.OVERTAKEN))
        advance(400)

        h.player.onProximity(active(intervalMillis = 300))
        runCurrent()
        advance(1_000)

        assertEquals(listOf(1_000L, 1_300L), h.output.beepTimes())
    }

    @Test
    fun `끔·러닝 끝·만료로 멈추면 비프음 자원을 놓는다`() = runTest {
        val h = harness()
        h.player.onProximity(active(intervalMillis = 500))
        runCurrent()
        assertTrue(h.output.releases.isEmpty())

        h.player.onProximity(inactive())
        runCurrent()
        assertEquals(1, h.output.releases.size)

        h.player.onProximity(active(intervalMillis = 500))
        runCurrent()
        h.player.stop()
        assertEquals(2, h.output.releases.size)

        h.player.onProximity(active(intervalMillis = 500))
        runCurrent()
        advance(ProximityAlertPlayer.EXPIRY_MILLIS)
        assertEquals(3, h.output.releases.size)
        assertEquals(h.output.releases.last(), testScheduler.currentTime)
    }

    @Test
    fun `새 메시지로 간격만 바뀔 때는 비프음 자원을 놓지 않는다`() = runTest {
        val h = harness()
        h.player.onProximity(active(intervalMillis = 500))
        runCurrent()
        advance(1_000)

        h.player.onProximity(active(intervalMillis = 300))
        h.player.onLeadChange(lead(LeadChangeMessage.Kind.OVERTOOK))
        runCurrent()
        advance(1_000)

        assertTrue(h.output.releases.isEmpty())
    }
}
