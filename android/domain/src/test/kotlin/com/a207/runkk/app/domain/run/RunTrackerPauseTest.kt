package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.testing.CallLog
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
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * RunTracker 일시정지·정지 한도(6.1.14, A-02, D-93, D-120, D-157): 시험 문서 2.2.14 TC-6321~TC-6333, 2.2.16 TC-6371~TC-6381,
 * 일시정지 중 구간 안내 생략 TC-6391~TC-6393. 시각 표기 "10:mm:ss"는 T0를 10:00:00으로 본 값이다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RunTrackerPauseTest {
    private val log = CallLog()
    private val pending = FakePendingRunRepository(log)
    private val watch = FakeWatchGateway()
    private val output = FakeGuidanceOutput()
    private val host = FakeMeasurementHost()
    private val settings = FakeSettingsRepository()
    private val clock = TestClock()

    private fun t(minutes: Long, seconds: Long = 0): Instant = T0.plusSeconds(minutes * 60 + seconds)

    private fun TestScope.tracker(): RunTracker = runTracker(
        pending,
        FakeUploadScheduler(log),
        watch,
        output,
        FakeLocationPermission(),
        host,
        settings,
        clock,
        backgroundScope
    )

    private fun spec(mode: RunMode, plan: PlanSnapshot? = null): RunStartSpec = RunStartSpec(
        mode = mode,
        cardKind = CardKind.FREE_RUN,
        battleId = if (mode == RunMode.BATTLE) 3 else null,
        recordId = "rec-1",
        todayPlan = plan
    )

    /** [startAt]에 [mode] 측정을 시작한다. */
    private fun TestScope.started(
        mode: RunMode = RunMode.FREE,
        startAt: Instant = t(0),
        plan: PlanSnapshot? = null
    ): RunTracker {
        clock.now = startAt
        val tracker = tracker()
        tracker.startRun(spec(mode, plan))
        runCurrent()
        return tracker
    }

    /** 정상 포인트(T0 기준 초). */
    private fun RunTracker.point(seconds: Long, meters: Double) {
        onLocationSample(sample(seconds, meters))
    }

    private fun RunTracker.pauseAt(at: Instant) {
        clock.now = at
        onPauseTapped()
    }

    private fun pauseGuidances(message: GuidanceMessage): Int =
        output.played.count { it.kind == Guidance.Kind.PAUSE && it.message == message }

    // ---------- 2.2.14 tick 정지 한도 ----------

    @Test
    fun `TC-6321 세션이 없으면 tick은 아무것도 하지 않는다`() = runTest {
        val tracker = tracker()
        runCurrent()
        tracker.tick(t(0, 5))
        assertEquals(RunProgress.INITIAL, tracker.progress.value)
        assertTrue(watch.published.isEmpty())
        assertTrue(output.played.isEmpty())
        assertEquals(0, host.stopped)
    }

    @Test
    fun `TC-6322 자유런 측정 중 tick은 정지 안내 없이 표시를 보낸다`() = runTest {
        val tracker = started()
        tracker.tick(t(5))
        val display = watch.published.last()
        assertTrue(display.isRunning)
        assertFalse(display.isPaused)
        assertTrue(display.isPausable)
        assertNull(display.notice)
        assertEquals(0, output.played.count { it.kind == Guidance.Kind.PAUSE })
    }

    @Test
    fun `TC-6323 정지 60분 전 1초는 끝내지 않고, 60분 정각과 그 뒤는 자동 종료한다`() = runTest {
        for (endTick in listOf(t(60), t(60, 1))) {
            val tracker = started(startAt = t(-30))
            tracker.point(-1_700, 0.0)
            tracker.point(-1_100, 3_000.0)
            tracker.pauseAt(t(0))
            tracker.tick(t(59, 59))
            assertEquals(0, host.stopped)
            val before = watch.published.size
            tracker.tick(endTick)
            runCurrent()
            val run = pending.runs.last()
            assertEquals(listOf(PauseInterval(t(0), endTick)), run.pauses)
            assertEquals(Duration.ofSeconds(1_800), run.elapsed)
            assertEquals(Distance(3_000), run.distance)
            assertEquals(1, host.stopped)
            assertEquals(1, pauseGuidances(GuidanceMessage.PAUSE_AUTO_ENDED))
            assertEquals(1, watch.published.size - before)
            assertFalse(watch.published.last().isRunning)
            assertEquals(WatchNotice.AUTO_ENDED, watch.published.last().notice)
            tracker.tick(endTick.plusSeconds(1))
            assertEquals(before + 1, watch.published.size, "세션 없음")
            host.stopped = 0
            output.played.clear()
        }
    }

    @Test
    fun `TC-6324 정지 54분 59초는 예고 없이 정지 표시만 보낸다`() = runTest {
        val tracker = started()
        tracker.pauseAt(t(0))
        tracker.tick(t(54, 59))
        assertEquals(0, pauseGuidances(GuidanceMessage.PAUSE_AUTO_END_SOON))
        assertTrue(watch.published.last().isPaused)
        assertNull(watch.published.last().notice)
        assertEquals(0, host.stopped)
    }

    @Test
    fun `TC-6325 55분 예고는 정지마다 한 번이고 워치 안내는 남는다`() = runTest {
        val tracker = started()
        tracker.pauseAt(t(0))
        for (now in listOf(t(55), t(55, 1), t(56))) {
            tracker.tick(now)
            assertEquals(WatchNotice.AUTO_END_SOON, watch.published.last().notice)
        }
        assertEquals(1, pauseGuidances(GuidanceMessage.PAUSE_AUTO_END_SOON))
    }

    @Test
    fun `TC-6326 첫 tick이 55분 정각을 건너뛰어도 예고한다`() = runTest {
        val expected = mapOf(t(54, 59) to 0, t(55) to 1, t(55, 1) to 1)
        for ((first, count) in expected) {
            output.played.clear()
            val tracker = started()
            tracker.pauseAt(t(0))
            tracker.tick(first)
            assertEquals(count, pauseGuidances(GuidanceMessage.PAUSE_AUTO_END_SOON), "$first")
            tracker.endRun()
        }
    }

    @Test
    fun `TC-6327 한도는 지금 정지만 세고 재개하면 예고 표시를 지운다`() = runTest {
        val tracker = started()
        tracker.point(1, 0.0)
        tracker.point(401, 2_000.0)
        tracker.pauseAt(t(0))
        tracker.tick(t(55))
        clock.now = t(56)
        tracker.onResumeTapped()
        assertNull(watch.published.last().notice)
        tracker.pauseAt(t(60))
        tracker.tick(t(114, 59))
        assertEquals(1, pauseGuidances(GuidanceMessage.PAUSE_AUTO_END_SOON))
        tracker.tick(t(115))
        assertEquals(2, pauseGuidances(GuidanceMessage.PAUSE_AUTO_END_SOON))
        tracker.tick(t(119, 59))
        assertEquals(0, host.stopped)
    }

    @Test
    fun `TC-6328 잠금 중 워치 정지도 같은 한도로 끝낸다`() = runTest {
        val tracker = started()
        tracker.point(1, 0.0)
        tracker.point(401, 2_000.0)
        tracker.lockScreen()
        clock.now = t(0, 3)
        watch.pauseRequests.emit(WatchGateway.PauseAction.PAUSE)
        runCurrent()
        assertTrue(tracker.progress.value.isPaused)
        tracker.tick(t(55, 3))
        assertEquals(1, pauseGuidances(GuidanceMessage.PAUSE_AUTO_END_SOON))
        tracker.tick(t(60, 3))
        runCurrent()
        assertEquals(1, pending.runs.size)
    }

    @Test
    fun `TC-6329 거리 0이면 자동 종료해도 저장하지 않고 안내만 낸다`() = runTest {
        val tracker = started()
        tracker.pauseAt(t(0, 10))
        tracker.tick(t(60, 10))
        runCurrent()
        assertTrue(pending.runs.isEmpty())
        assertTrue(log.calls.none { it == "pending.save" })
        assertEquals(1, host.stopped)
        assertEquals(1, pauseGuidances(GuidanceMessage.PAUSE_AUTO_ENDED))
        assertEquals(WatchNotice.AUTO_ENDED, watch.published.last().notice)
    }

    @Test
    fun `정지 60분 자동 종료는 ended로 끝난 결과를 알린다 (러닝 화면이 결과·홈으로 간다, FR-195)`() = runTest {
        val tracker = started()
        val ended = mutableListOf<FinishOutcome>()
        backgroundScope.launch { tracker.ended.collect { ended += it } }
        runCurrent()
        tracker.point(1, 0.0)
        tracker.point(101, 500.0)
        tracker.pauseAt(t(0, 3))
        tracker.tick(t(60, 3))
        runCurrent()
        assertEquals(1, ended.size)
        assertIs<FinishOutcome.Saved>(ended.single())
    }

    @Test
    fun `거리 0 자동 종료와 워치 종료도 ended로 알리고, 잠금 중 거부는 알리지 않는다`() = runTest {
        val tracker = started()
        val ended = mutableListOf<FinishOutcome>()
        backgroundScope.launch { tracker.ended.collect { ended += it } }
        runCurrent()
        tracker.lockScreen()
        watch.endRequests.emit(Unit)
        runCurrent()
        assertTrue(ended.isEmpty())
        tracker.onUnlockLongPressed()
        tracker.pauseAt(t(0, 10))
        tracker.tick(t(60, 10))
        runCurrent()
        assertEquals(listOf<FinishOutcome>(FinishOutcome.NotSaved), ended)
    }

    @Test
    fun `TC-6330 정지 중에는 구간·호흡 안내를 평가하지 않는다`() = runTest {
        settings.snapshot = settings.snapshot.copy(birthYear = 1996)
        val tracker = started()
        tracker.point(1, 0.0)
        tracker.point(201, 999.0)
        tracker.pauseAt(t(0, 3))
        for (s in 0 until 10L) {
            watch.heartRates.emit(listOf(HeartRateSample(t(1, s), 180)))
            runCurrent()
            tracker.tick(t(9, s))
        }
        assertTrue(output.played.none { it.kind == Guidance.Kind.SPLIT || it.kind == Guidance.Kind.BREATH })
    }

    @Test
    fun `TC-6332 정지 중 워치 표시는 폰 진행값과 같고 현재 페이스는 비며 심박은 갱신한다`() = runTest {
        for (lock in listOf(false, true)) {
            watch.published.clear()
            val tracker = started()
            tracker.point(6 * 60 + 40, 0.0)
            tracker.point(10 * 60, 1_000.0)
            tracker.tick(t(10))
            tracker.pauseAt(t(10))
            if (lock) tracker.lockScreen()
            watch.heartRates.emit(listOf(HeartRateSample(t(11), 160)))
            runCurrent()
            tracker.tick(t(12))
            val display = watch.published.last()
            assertNull(display.currentPace)
            assertEquals(160, display.heartRateBpm)
            assertTrue(display.isPaused)
            assertTrue(display.isPausable)
            assertEquals(Duration.ofSeconds(600), display.elapsed)
            assertEquals(tracker.progress.value.heartRate?.bpm, display.heartRateBpm)
            tracker.endRun()
        }
    }

    @Test
    fun `TC-6333 예고 뒤 폰 재개와 워치 재개 모두 다음 표시의 안내를 지운다`() = runTest {
        val phone = started()
        phone.pauseAt(t(0))
        phone.tick(t(55))
        clock.now = t(55, 30)
        phone.onResumeTapped()
        phone.tick(t(56))
        assertNull(watch.published.last().notice)
        phone.endRun()

        val viaWatch = started()
        viaWatch.lockScreen()
        clock.now = t(0)
        watch.pauseRequests.emit(WatchGateway.PauseAction.PAUSE)
        runCurrent()
        viaWatch.tick(t(55))
        assertEquals(WatchNotice.AUTO_END_SOON, watch.published.last().notice)
        clock.now = t(55, 30)
        watch.pauseRequests.emit(WatchGateway.PauseAction.RESUME)
        runCurrent()
        viaWatch.tick(t(56))
        assertNull(watch.published.last().notice)
    }

    // ---------- 2.2.16 일시정지·재개 ----------

    @Test
    fun `TC-6371 폰 일시정지는 PAUSED 안내와 정지 표시를 보낸다`() = runTest {
        val tracker = started()
        tracker.pauseAt(t(10))
        assertTrue(tracker.progress.value.isPaused)
        assertEquals(Guidance.screenOnly(Guidance.Kind.PAUSE, GuidanceMessage.PAUSED), output.played.single())
        assertTrue(watch.published.last().isPaused)
        assertTrue(watch.published.last().isPausable)
    }

    @Test
    fun `TC-6372 대결·크루런은 일시정지하지 않는다`() = runTest {
        for (mode in listOf(RunMode.BATTLE, RunMode.CREW_RUN)) {
            val tracker = started(mode)
            val before = watch.published.size
            tracker.pauseAt(t(10))
            assertFalse(tracker.progress.value.isPaused, "$mode")
            assertTrue(output.played.isEmpty())
            assertEquals(before, watch.published.size)
            tracker.endRun()
        }
    }

    @Test
    fun `TC-6373 잠금 중 폰 일시정지 버튼은 받지 않는다`() = runTest {
        val tracker = started()
        tracker.lockScreen()
        tracker.pauseAt(t(10))
        assertFalse(tracker.progress.value.isPaused)
        assertTrue(tracker.progress.value.isLocked)
        assertTrue(output.played.isEmpty())
    }

    @Test
    fun `TC-6374 폰 재개는 정지 구간을 닫고 RESUMED 안내와 표시를 보낸다`() = runTest {
        val tracker = started()
        tracker.point(1, 0.0)
        tracker.point(201, 1_000.0)
        tracker.pauseAt(t(10))
        clock.now = t(15)
        tracker.onResumeTapped()
        assertFalse(tracker.progress.value.isPaused)
        assertEquals(GuidanceMessage.RESUMED, output.played.last().message)
        assertFalse(watch.published.last().isPaused)
        clock.now = t(20)
        tracker.onFinishLongPressed()
        runCurrent()
        assertEquals(listOf(PauseInterval(t(10), t(15))), pending.runs.single().pauses)
    }

    @Test
    fun `TC-6375 PAUSED_LOCKED에서는 폰 재개·정지 버튼을 받지 않는다`() = runTest {
        val tracker = started()
        tracker.pauseAt(t(10))
        tracker.lockScreen()
        output.played.clear()
        clock.now = t(11)
        tracker.onResumeTapped()
        tracker.onPauseTapped()
        assertTrue(tracker.progress.value.isPaused)
        assertTrue(tracker.progress.value.isLocked)
        assertTrue(output.played.isEmpty())
    }

    @Test
    fun `TC-6376 잠금 중 워치 정지는 잠금을 유지한 정지다`() = runTest {
        val tracker = started()
        tracker.lockScreen()
        clock.now = t(10)
        watch.pauseRequests.emit(WatchGateway.PauseAction.PAUSE)
        runCurrent()
        assertTrue(tracker.progress.value.isPaused)
        assertTrue(tracker.progress.value.isLocked)
        assertEquals(GuidanceMessage.PAUSED, output.played.single().message)
        assertTrue(watch.published.last().isPaused)
    }

    @Test
    fun `TC-6377 PAUSED_LOCKED에서 워치 재개는 잠금으로 돌아간다`() = runTest {
        val tracker = started()
        tracker.point(1, 0.0)
        tracker.point(201, 1_000.0)
        tracker.lockScreen()
        clock.now = t(10)
        watch.pauseRequests.emit(WatchGateway.PauseAction.PAUSE)
        runCurrent()
        clock.now = t(15)
        watch.pauseRequests.emit(WatchGateway.PauseAction.RESUME)
        runCurrent()
        assertFalse(tracker.progress.value.isPaused)
        assertTrue(tracker.progress.value.isLocked)
        assertEquals(GuidanceMessage.RESUMED, output.played.last().message)
        assertFalse(watch.published.last().isPaused)
        tracker.onUnlockLongPressed()
        clock.now = t(20)
        tracker.onFinishLongPressed()
        runCurrent()
        assertEquals(listOf(PauseInterval(t(10), t(15))), pending.runs.single().pauses)
    }

    @Test
    fun `TC-6378 대결 중 워치 정지는 무시하고 다음 표시로 바로잡는다`() = runTest {
        val tracker = started(RunMode.BATTLE)
        watch.pauseRequests.emit(WatchGateway.PauseAction.PAUSE)
        runCurrent()
        assertFalse(tracker.progress.value.isPaused)
        assertTrue(output.played.isEmpty())
        tracker.tick(t(1))
        assertFalse(watch.published.last().isPausable)
        assertFalse(watch.published.last().isPaused)
    }

    @Test
    fun `TC-6379 워치 정지 두 번은 첫 요청만 받고 정지 시작은 폰 처리 시각이다`() = runTest {
        val tracker = started()
        tracker.point(1, 0.0)
        tracker.point(201, 1_000.0)
        clock.now = t(10)
        watch.pauseRequests.emit(WatchGateway.PauseAction.PAUSE)
        runCurrent()
        clock.now = t(10, 1)
        watch.pauseRequests.emit(WatchGateway.PauseAction.PAUSE)
        runCurrent()
        assertEquals(1, pauseGuidances(GuidanceMessage.PAUSED))
        clock.now = t(12)
        tracker.onFinishLongPressed()
        runCurrent()
        assertEquals(t(10), pending.runs.single().pauses.single().from)
    }

    @Test
    fun `TC-6380 정지 중 종료는 마지막 정지를 닫고 서비스 정지·시작 화면 뒤 저장한다`() = runTest {
        val tracker = started()
        tracker.point(1, 0.0)
        tracker.point(201, 1_000.0)
        tracker.pauseAt(t(10))
        clock.now = t(12)
        val run = assertIs<FinishOutcome.Saved>(tracker.onFinishLongPressed()).run
        assertEquals(listOf(PauseInterval(t(10), t(12))), run.pauses)
        assertEquals(1, host.stopped)
        assertFalse(watch.published.last().isRunning)
        assertTrue(log.calls.isEmpty(), "저장은 applicationScope에서 뒤에 돈다")
        runCurrent()
        assertEquals(listOf("pending.save", "upload.requestUpload"), log.calls)
    }

    @Test
    fun `TC-6381 PAUSED_LOCKED에서 폰·워치 종료는 거부하고 해제 안내를 낸다`() = runTest {
        val tracker = started()
        tracker.point(1, 0.0)
        tracker.point(201, 1_000.0)
        tracker.pauseAt(t(10))
        tracker.lockScreen()
        assertEquals(FinishOutcome.Rejected, tracker.onFinishLongPressed())
        assertEquals(Guidance.Kind.UNLOCK_HINT, output.played.last().kind)
        output.played.clear()
        watch.endRequests.emit(Unit)
        runCurrent()
        assertEquals(Guidance.Kind.UNLOCK_HINT, output.played.single().kind)
        assertEquals(0, host.stopped)
        assertTrue(log.calls.isEmpty())
    }

    // ---------- 일시정지 중 구간 안내 생략 (TC-6391~TC-6393) ----------

    @Test
    fun `TC-6391 1km 안내 거리는 정지 구간을 뺀 누적 거리다`() = runTest {
        val tracker = started()
        // 980m를 초속 5m로 달린 뒤(이웃 포인트 사이 25km/h 이하, FR-047) 정지한다. r초만큼 뒤 시각으로 민다.
        val r = 195L
        tracker.point(1, 0.0)
        tracker.point(2 + r, 980.0)
        tracker.tick(at(2 + r))
        tracker.pauseAt(at(3 + r))
        for (s in 4..33L) {
            tracker.point(s + r, 980.0 + (s - 3) * 10)
            tracker.tick(at(s + r))
        }
        clock.now = at(40 + r)
        tracker.onResumeTapped()
        tracker.point(41 + r, 1_300.0)
        tracker.tick(at(41 + r))
        for ((i, meters) in listOf(1_305.0, 1_310.0, 1_315.0).withIndex()) {
            tracker.point(42L + i + r, meters)
            tracker.tick(at(42L + i + r))
        }
        assertTrue(output.played.none { it.kind == Guidance.Kind.SPLIT })
        tracker.point(45 + r, 1_320.0)
        tracker.tick(at(45 + r))
        assertEquals("1", output.played.single { it.kind == Guidance.Kind.SPLIT }.args.single().toString())
    }

    @Test
    fun `TC-6392 목표 도달은 정지 중에 내지 않고 재개 뒤 운동 거리로 낸다`() = runTest {
        val tracker = started(plan = PlanSnapshot(Distance(5_000), null))
        tracker.point(1, 0.0)
        tracker.point(1_000, 4_990.0)
        tracker.tick(at(1_000))
        tracker.pauseAt(at(1_001))
        tracker.point(1_002, 5_100.0)
        tracker.tick(at(1_600))
        assertTrue(output.played.none { it.kind == Guidance.Kind.TARGET_REACHED })
        clock.now = at(1_603)
        tracker.onResumeTapped()
        tracker.point(1_604, 6_000.0)
        tracker.point(1_606, 6_010.0)
        tracker.tick(at(1_606))
        assertEquals(1, output.played.count { it.kind == Guidance.Kind.TARGET_REACHED })
    }

    @Test
    fun `TC-6393 페이스 안내는 운동 시간 기준 평균으로 판정한다`() = runTest {
        settings.snapshot =
            settings.snapshot.copy(
                guidanceSettings = settings.snapshot.guidanceSettings.copy(isFollowingCoachPlan = true)
            )
        val plan = PlanSnapshot(Distance(5_000), Pace(300))
        for ((arriveAt, expected) in listOf(941L to 1, 921L to 0)) {
            output.played.clear()
            val tracker = started(plan = plan)
            tracker.point(1, 0.0)
            tracker.point(100, 500.0)
            tracker.tick(at(100))
            tracker.pauseAt(at(100))
            clock.now = at(700)
            tracker.onResumeTapped()
            tracker.point(701, 900.0)
            tracker.point(arriveAt, 1_400.0)
            tracker.tick(at(arriveAt))
            assertEquals(expected, output.played.count { it.kind == Guidance.Kind.PACE }, "도착 $arriveAt")
            tracker.endRun()
        }
    }
}
