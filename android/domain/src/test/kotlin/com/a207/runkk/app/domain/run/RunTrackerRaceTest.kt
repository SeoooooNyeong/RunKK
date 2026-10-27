package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.testing.FakeGuidanceOutput
import com.a207.runkk.app.domain.testing.FakeLocationPermission
import com.a207.runkk.app.domain.testing.FakeMeasurementHost
import com.a207.runkk.app.domain.testing.FakePendingRunRepository
import com.a207.runkk.app.domain.testing.FakeSettingsRepository
import com.a207.runkk.app.domain.testing.FakeUploadScheduler
import com.a207.runkk.app.domain.testing.FakeWatchGateway
import com.a207.runkk.app.domain.testing.T0
import com.a207.runkk.app.domain.testing.TestClock
import com.a207.runkk.app.domain.testing.runTracker
import java.time.Duration
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

/**
 * RunTracker.tick의 판단과 다른 스레드의 재개·종료가 겹칠 때(AN5-C-7, FR-195, D-157 (7)). 실제 스레드 둘을 같은 순간에 출발시켜
 * 여러 번 겹친다. 1초 시계(tick)와 화면·워치 요청은 실제로 다른 스레드에서 온다.
 */
class RunTrackerRaceTest {
    private val threads: ExecutorService = Executors.newFixedThreadPool(2)
    private val barrier = CyclicBarrier(2)

    @AfterTest
    fun tearDown() {
        threads.shutdownNow()
    }

    private class Rig {
        val scope = CoroutineScope(Job())
        val clock = TestClock(T0)
        val output = FakeGuidanceOutput()
        val host = FakeMeasurementHost()
        val watch = FakeWatchGateway()
        val tracker =
            runTracker(
                FakePendingRunRepository(),
                FakeUploadScheduler(),
                watch,
                output,
                FakeLocationPermission(),
                host,
                FakeSettingsRepository(),
                clock,
                scope
            )

        fun start() {
            tracker.startRun(RunStartSpec(RunMode.FREE, CardKind.FREE_RUN, battleId = null, recordId = "rec-1"))
        }
    }

    /** 두 일을 같은 순간에 출발시켜 끝까지 기다린다. */
    private fun race(first: () -> Unit, second: () -> Unit) {
        val a =
            threads.submit {
                barrier.await()
                first()
            }
        val b =
            threads.submit {
                barrier.await()
                second()
            }
        a.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        b.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    @Test
    fun `AN5-C-7 정지 60분 자동 종료 판단과 재개가 겹쳐도 재개한 러닝을 끝내지 않는다`() {
        var resumedThenEnded = 0
        repeat(ROUNDS) {
            val rig = Rig()
            rig.start()
            val pausedAt = T0.plusSeconds(60)
            rig.clock.now = pausedAt
            rig.tracker.onPauseTapped()
            val limit = pausedAt.plus(PAUSE_LIMIT)
            rig.clock.now = limit

            race({ rig.tracker.tick(limit) }, { rig.tracker.onResumeTapped() })

            val isResumed = rig.output.played.any { it.message == GuidanceMessage.RESUMED }
            if (isResumed && rig.host.stopped > 0) resumedThenEnded++
            rig.scope.cancel()
        }

        assertEquals(0, resumedThenEnded, "재개 안내를 낸 뒤 자동 종료한 횟수")
    }

    @Test
    fun `AN5-C-7 끝난 뒤 늦게 도착한 tick은 워치에 달리는 중 표시를 보내지 않는다`() {
        var runningAfterEnd = 0
        repeat(ROUNDS) {
            val rig = Rig()
            rig.start()
            val now = T0.plusSeconds(60)
            rig.clock.now = now

            race({ rig.tracker.tick(now) }, { rig.tracker.endRun() })

            if (rig.watch.published.last().isRunning) runningAfterEnd++
            rig.scope.cancel()
        }

        assertEquals(0, runningAfterEnd, "종료 뒤 마지막 워치 표시가 달리는 중인 횟수")
    }

    @Test
    fun `AN5-C-7 일시정지와 종료가 겹쳐도 끝난 뒤 워치에 달리는 중 표시를 보내지 않는다`() {
        var runningAfterEnd = 0
        repeat(ROUNDS) {
            val rig = Rig()
            rig.start()
            rig.clock.now = T0.plusSeconds(60)

            race({ rig.tracker.onPauseTapped() }, { rig.tracker.endRun() })

            if (rig.watch.published.last().isRunning) runningAfterEnd++
            rig.scope.cancel()
        }

        assertEquals(0, runningAfterEnd, "종료 뒤 마지막 워치 표시가 달리는 중인 횟수")
    }

    private companion object {
        const val ROUNDS = 20_000
        const val TIMEOUT_SECONDS = 5L
        val PAUSE_LIMIT: Duration = Duration.ofMinutes(60)
    }
}
