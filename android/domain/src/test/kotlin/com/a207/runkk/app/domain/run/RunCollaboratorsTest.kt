package com.a207.runkk.app.domain.run

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
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
import java.time.Clock
import java.time.Duration
import javax.inject.Singleton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** RunTracker 협력 객체 넷(D-95)과 RunWatchBridge(D-119, D-158): 시험 문서 2.2.16 TC-6382~TC-6389, TC-6401~TC-6410. */
@OptIn(ExperimentalCoroutinesApi::class)
class RunCollaboratorsTest {
    private val log = CallLog()
    private val pending = FakePendingRunRepository(log)
    private val scheduler = FakeUploadScheduler(log)
    private val watch = FakeWatchGateway()
    private val output = FakeGuidanceOutput()
    private val permission = FakeLocationPermission()
    private val host = FakeMeasurementHost()
    private val settings = FakeSettingsRepository()
    private val clock = TestClock()

    private fun TestScope.tracker(): RunTracker =
        runTracker(pending, scheduler, watch, output, permission, host, settings, clock, backgroundScope)

    private fun TestScope.started(mode: RunMode): RunTracker = tracker().also {
        it.startRun(RunStartSpec(mode = mode, cardKind = CardKind.FREE_RUN, recordId = "rec-$mode"))
        runCurrent()
    }

    private fun completed(): CompletedRun = CompletedRun(
        recordId = "r1",
        mode = RunMode.FREE,
        cardKind = CardKind.FREE_RUN,
        battleId = null,
        crewRunPostId = null,
        startedAt = T0,
        elapsed = Duration.ofMinutes(10),
        distance = Distance(2_000),
        route = emptyList(),
        unstable = emptyList(),
        heartRates = emptyList(),
        savedAt = T0
    )

    private fun alert(gap: Int, millis: Long, isAhead: Boolean = true) =
        ProximityAlert(gap, Duration.ofMillis(millis), isAhead)

    // ---------- 생성자 (TC-6382) ----------

    @Test
    fun `TC-6382 RunTracker 생성자는 협력 객체 넷이고 시계를 받지 않는다`() {
        val ctor = RunTracker::class.java.constructors.single()
        assertEquals(
            listOf(
                RunArchive::class.java,
                RunWatchBridge::class.java,
                RunCoach::class.java,
                MeasurementRuntime::class.java
            ),
            ctor.parameterTypes.toList()
        )
        assertNotNull(ctor.getAnnotation(javax.inject.Inject::class.java))
        assertNotNull(RunTracker::class.java.getAnnotation(Singleton::class.java))
        fun params(type: Class<*>) = type.constructors.single().parameterTypes.toList()
        assertEquals(
            listOf(PendingRunRepository::class.java, UploadScheduler::class.java, CoroutineScope::class.java),
            params(RunArchive::class.java)
        )
        assertEquals(listOf(WatchGateway::class.java, CoroutineScope::class.java), params(RunWatchBridge::class.java))
        assertEquals(listOf(GuidanceOutput::class.java, SettingsRepository::class.java), params(RunCoach::class.java))
        assertEquals(
            listOf(
                LocationPermission::class.java,
                MeasurementHost::class.java,
                SettingsRepository::class.java,
                Clock::class.java
            ),
            params(MeasurementRuntime::class.java)
        )
    }

    // ---------- RunArchive (TC-6383, TC-6384) ----------

    @Test
    fun `TC-6383 saveInBackground는 기다리지 않고 세 번까지 시도한다`() = runTest {
        for ((failures, expected) in listOf(3 to Triple(3, 0, 1), 2 to Triple(3, 1, 0), 0 to Triple(1, 1, 0))) {
            log.calls.clear()
            scheduler.requests = 0
            pending.saveFailuresLeft = failures
            var failed = 0
            RunArchive(pending, scheduler, backgroundScope).saveInBackground(completed()) { failed++ }
            assertTrue(log.calls.isEmpty(), "호출자는 바로 돌아온다")
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(expected.first, log.calls.count { it == "pending.save" }, "실패 $failures")
            assertEquals(expected.second, scheduler.requests)
            assertEquals(expected.third, failed)
        }
    }

    @Test
    fun `TC-6384 늦은 심박은 다시 할 만한 실패면 다음 묶음과 합치고 거부는 버린다`() = runTest {
        val archive = RunArchive(pending, scheduler, backgroundScope)
        pending.appendFailWith = AppError.Storage
        archive.appendLateHeartRates("r1", listOf(HeartRateSample(T0, 100), HeartRateSample(T0.plusSeconds(1), 101)))
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(3, log.calls.count { it == "pending.appendHeartRates:r1" })
        pending.appendFailWith = null
        archive.appendLateHeartRates("r1", listOf(HeartRateSample(T0.plusSeconds(2), 102)))
        runCurrent()
        assertEquals(3, pending.appended.single().second.size)

        pending.appended.clear()
        log.calls.clear()
        pending.appendFailWith = AppError.Rejected("RECORD_NOT_FOUND")
        archive.appendLateHeartRates("r2", listOf(HeartRateSample(T0, 90), HeartRateSample(T0.plusSeconds(1), 91)))
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, log.calls.count { it == "pending.appendHeartRates:r2" })
        pending.appendFailWith = null
        archive.appendLateHeartRates("r2", listOf(HeartRateSample(T0.plusSeconds(2), 92)))
        runCurrent()
        assertEquals(listOf(HeartRateSample(T0.plusSeconds(2), 92)), pending.appended.single().second)
    }

    // ---------- MeasurementRuntime·RunCoach (TC-6385~TC-6389) ----------

    @Test
    fun `TC-6385 checkStartable은 OS 권한과 서버 동의가 모두 있어야 성공이다`() {
        val cases = listOf(true to true, false to true, true to false, false to false)
        val results =
            cases.map { (granted, consented) ->
                permission.granted = granted
                settings.snapshot = settings.snapshot.copy(isLocationConsented = consented)
                MeasurementRuntime(permission, host, settings, clock).checkStartable()
            }
        val rejected = Outcome.Failure(AppError.Rejected(AppError.Rejected.LOCATION_CONSENT))
        assertEquals(listOf(Outcome.Success(Unit), rejected, rejected, rejected), results)
    }

    @Test
    fun `TC-6386 측정 시작 시각은 MeasurementRuntime의 시계에서 온다`() = runTest {
        clock.now = T0.plusSeconds(36_000)
        val tracker = started(RunMode.FREE)
        tracker.onLocationSample(sample(36_001, 0.0))
        tracker.onLocationSample(sample(36_003, 10.0))
        val run = (tracker.endRun() as FinishOutcome.Saved).run
        assertEquals(T0.plusSeconds(36_000), run.startedAt)
    }

    @Test
    fun `TC-6387 RunCoach는 회마다 규칙을 새로 만든다`() {
        val coach = RunCoach(output, settings)
        repeat(2) {
            coach.begin(RunMode.FREE)
            val session = RunSession(freeSpec()).also { it.start(T0) }
            coach.guide(progress(999), session)
            coach.guide(progress(1_000), session)
        }
        assertEquals(2, output.played.count { it.kind == Guidance.Kind.SPLIT })
    }

    @Test
    fun `TC-6388 proximityOf는 근접 알림 설정을 넘겨 규칙에 맡긴다`() {
        val coach = RunCoach(output, settings)
        assertEquals(alert(24, 640), coach.proximityOf(24, isWatchConnected = true))
        settings.snapshot =
            settings.snapshot.copy(guidanceSettings = GuidanceSettings.DEFAULT.copy(isProximityAlertOn = false))
        assertNull(coach.proximityOf(24, isWatchConnected = true))
    }

    @Test
    fun `TC-6389 진동·근접 알림 여부는 설정 사본에서 읽고 사본이 없으면 둘 다 참이다`() {
        settings.snapshot =
            settings.snapshot.copy(
                guidanceSettings = GuidanceSettings.DEFAULT.copy(isBattleVibrationOn = true, isProximityAlertOn = false)
            )
        val coach = RunCoach(output, settings)
        assertEquals(true to false, coach.isBattleVibrationOn() to coach.isProximityAlertOn())
        settings.snapshot = SettingsSnapshot.SAFE
        assertEquals(true to true, coach.isBattleVibrationOn() to coach.isProximityAlertOn())
    }

    // ---------- RunWatchBridge (TC-6401~TC-6410) ----------

    @Test
    fun `TC-6401 워치 표시의 isPausable·isPaused는 모드와 정지 상태를 따른다`() = runTest {
        val seen = mutableListOf<Pair<Boolean, Boolean>>()
        for (mode in RunMode.entries) {
            val tracker = started(mode)
            tracker.tick(T0.plusSeconds(1))
            seen += watch.published.last().isPausable to watch.published.last().isPaused
            tracker.endRun()
        }
        val paused = started(RunMode.FREE)
        clock.now = T0.plusSeconds(2)
        paused.onPauseTapped()
        paused.tick(T0.plusSeconds(3))
        seen += watch.published.last().isPausable to watch.published.last().isPaused
        assertEquals(listOf(true to false, false to false, true to false, true to true), seen)
    }

    @Test
    fun `TC-6402 근접 알림은 켜짐·간격 변화·2초 유지 신호·꺼짐 때만 보낸다`() = runTest {
        val tracker = started(RunMode.BATTLE)
        listOf(81, 81, 81, 79, 24, 200, 200).forEachIndexed { second, gap ->
            clock.now = T0.plusSeconds(second.toLong())
            tracker.onGapChanged(gap)
        }
        assertEquals(listOf(alert(81, 1_660), alert(81, 1_660), alert(24, 640), null), watch.proximity)
    }

    @Test
    fun `TC-6403 워치가 연결되지 않았으면 보내지 않는다`() = runTest {
        val tracker = started(RunMode.BATTLE)
        watch.connected = false
        tracker.onGapChanged(30)
        assertTrue(watch.proximity.isEmpty())
        watch.connected = true
        clock.now = T0.plusSeconds(1)
        tracker.onGapChanged(30)
        assertEquals(1, watch.proximity.size)
    }

    @Test
    fun `TC-6404 추월 안내는 화면에 보이고 워치 긴 진동은 근접 알림 설정만 따른다`() = runTest {
        val cases = listOf(true to true, true to false, false to true)
        val tracker = tracker()
        val result =
            cases.map { (vibration, proximity) ->
                settings.snapshot =
                    settings.snapshot.copy(
                        guidanceSettings =
                            GuidanceSettings.DEFAULT.copy(
                                isBattleVibrationOn = vibration,
                                isProximityAlertOn = proximity
                            )
                    )
                watch.leadChanges.clear()
                tracker.onLeadChanged(Guidance.Kind.OVERTOOK, GuidanceMessage.OVERTOOK, listOf(70))
                val played = output.played.last()
                Triple(played.hasVoice, played.hasVibration, watch.leadChanges.toList())
            }
        assertEquals(
            listOf(
                Triple(false, true, listOf(Guidance.Kind.OVERTOOK)),
                Triple(false, true, emptyList()),
                Triple(false, false, listOf(Guidance.Kind.OVERTOOK))
            ),
            result
        )
    }

    @Test
    fun `TC-6405 bind는 요청 종류마다 다른 연산으로 넘긴다`() = runTest {
        val calls = mutableListOf<String>()
        val listener =
            object : WatchRequestListener {
                override fun onStartRequested() {
                    calls += "start"
                }

                override fun onEndRequested() {
                    calls += "end"
                }

                override fun onPauseRequested() {
                    calls += "pause"
                }

                override fun onResumeRequested() {
                    calls += "resume"
                }

                override fun onHeartRates(samples: List<HeartRateSample>) {
                    calls += "heart:${samples.size}"
                }
            }
        RunWatchBridge(watch, backgroundScope).bind(listener)
        runCurrent()
        watch.startRequests.emit(Unit)
        watch.endRequests.emit(Unit)
        watch.pauseRequests.emit(WatchGateway.PauseAction.PAUSE)
        watch.pauseRequests.emit(WatchGateway.PauseAction.RESUME)
        watch.heartRates.emit(List(3) { HeartRateSample(T0.plusSeconds(it.toLong()), 120) })
        runCurrent()
        assertEquals(listOf("start", "end", "pause", "resume", "heart:3"), calls)
    }

    @Test
    fun `TC-6406 새 회의 첫 격차는 앞 회의 전송 기록과 무관하게 바로 보낸다`() = runTest {
        val first = started(RunMode.BATTLE)
        first.onGapChanged(50)
        clock.now = T0.plusSeconds(1)
        first.endRun()
        val second = started(RunMode.BATTLE)
        second.onGapChanged(50)
        assertEquals(listOf<ProximityAlert?>(alert(50, 1_110), alert(50, 1_110)), watch.proximity)
    }

    @Test
    fun `TC-6407 isWatchConnected는 WatchGateway 값을 그대로 돌려준다`() = runTest {
        val bridge = RunWatchBridge(watch, backgroundScope)
        watch.connected = true
        assertTrue(bridge.isWatchConnected())
        watch.connected = false
        assertEquals(false, bridge.isWatchConnected())
    }

    @Test
    fun `TC-6409 격차 null은 근접 알림을 바로 끄고 다음 격차로 다시 켠다`() = runTest {
        val tracker = started(RunMode.BATTLE)
        tracker.onGapChanged(50)
        clock.now = T0.plusSeconds(1)
        tracker.onGapChanged(null)
        clock.now = T0.plusSeconds(2)
        tracker.onGapChanged(null)
        clock.now = T0.plusSeconds(3)
        tracker.onGapChanged(50)
        assertEquals(listOf(alert(50, 1_110), null, alert(50, 1_110)), watch.proximity)
    }

    @Test
    fun `TC-6410 resetProximity 뒤에는 같은 알림도 다시 보낸다`() = runTest {
        val reset = RunWatchBridge(watch, backgroundScope)
        reset.publishProximity(alert(50, 1_110), T0)
        reset.resetProximity()
        reset.publishProximity(alert(50, 1_110), T0.plusSeconds(1))
        assertEquals(2, watch.proximity.size)

        watch.proximity.clear()
        val kept = RunWatchBridge(watch, backgroundScope)
        kept.publishProximity(alert(50, 1_110), T0)
        kept.publishProximity(alert(50, 1_110), T0.plusSeconds(1))
        assertEquals(1, watch.proximity.size)
    }

    @Test
    fun `요청 처리 중 예외가 나도 워치 요청 구독은 이어 간다`() = runTest {
        var starts = 0
        val listener =
            object : WatchRequestListener {
                override fun onStartRequested() {
                    starts++
                    if (starts == 1) error("first request fails")
                }

                override fun onEndRequested() = Unit

                override fun onPauseRequested() = Unit

                override fun onResumeRequested() = Unit

                override fun onHeartRates(samples: List<HeartRateSample>) = Unit
            }
        val handled = mutableListOf<Throwable>()
        val appScope =
            CoroutineScope(backgroundScope.coroutineContext + CoroutineExceptionHandler { _, e -> handled += e })
        val bridge = RunWatchBridge(watch, appScope)
        bridge.bind(listener)
        runCurrent()
        watch.startRequests.emit(Unit)
        watch.startRequests.emit(Unit)
        runCurrent()
        assertEquals(2, starts)
        assertEquals(listOf("first request fails"), handled.map { it.message }, "앱 범위 예외 처리기로 넘겨 운영 로그에 남긴다(AN5-C-13)")
    }
}
