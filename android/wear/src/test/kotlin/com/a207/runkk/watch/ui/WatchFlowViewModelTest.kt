package com.a207.runkk.watch.ui

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.MySummary
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import com.a207.runkk.app.domain.battle.BattleRunState
import com.a207.runkk.app.domain.battle.MatchAcceptance
import com.a207.runkk.app.domain.battle.OfflineRecordBattle
import com.a207.runkk.app.domain.battle.OpponentTrack
import com.a207.runkk.app.domain.battle.RecordBattleKind
import com.a207.runkk.app.domain.battle.RecordBattleStart
import com.a207.runkk.app.domain.live.BattleStart
import com.a207.runkk.app.domain.live.CountdownStarted
import com.a207.runkk.app.domain.live.MatchFailed
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceMessage
import com.a207.runkk.app.domain.run.LocationSample
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.watch.battle.RecordBattleMemory
import com.a207.runkk.watch.battle.WatchBattleFlow
import com.a207.runkk.watch.battle.WatchResultHeadline
import com.a207.runkk.watch.cache.OpponentRecordCache
import com.a207.runkk.watch.guide.WatchGuidanceOutput
import com.a207.runkk.watch.heart.HeartRateCollector
import com.a207.runkk.watch.link.MatchWaiter
import com.a207.runkk.watch.link.NetworkState
import com.a207.runkk.watch.link.PhoneLink
import com.a207.runkk.watch.link.WearMessage
import com.a207.runkk.watch.run.LocalWatchGateway
import com.a207.runkk.watch.session.WatchSession
import com.a207.runkk.watch.session.WatchSessionStore
import com.a207.runkk.watch.testing.FakeAccountRepository
import com.a207.runkk.watch.testing.FakeAlertOutput
import com.a207.runkk.watch.testing.FakeBattleRepository
import com.a207.runkk.watch.testing.FakeGuidanceOutput
import com.a207.runkk.watch.testing.FakeHeartRateSensor
import com.a207.runkk.watch.testing.FakeLiveChannelProvider
import com.a207.runkk.watch.testing.FakeMeasurementHost
import com.a207.runkk.watch.testing.FakePendingRunRepository
import com.a207.runkk.watch.testing.FakeSensorPermission
import com.a207.runkk.watch.testing.FakeSessionRepository
import com.a207.runkk.watch.testing.FakeSettingsRepository
import com.a207.runkk.watch.testing.FakeUploadScheduler
import com.a207.runkk.watch.testing.FakeWearDataLayer
import com.a207.runkk.watch.testing.SchedulerClock
import com.a207.runkk.watch.testing.SchedulerMonotonicClock
import com.a207.runkk.watch.testing.T0
import com.a207.runkk.watch.testing.alertPlayer
import com.a207.runkk.watch.testing.celebrityListItem
import com.a207.runkk.watch.testing.celebrityTrack
import com.a207.runkk.watch.testing.runTracker
import com.a207.runkk.watch.testing.selfTrack
import com.a207.runkk.wear.contract.SessionRevokedMessage
import com.a207.runkk.wear.contract.WatchDisplayMessage
import com.a207.runkk.wear.contract.WatchSessionMessage
import com.a207.runkk.wear.contract.WearPaths
import java.io.File
import java.time.Duration
import java.util.Locale
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech

/**
 * WatchFlowViewModel 상태 전이(상태도 2.4.25, D-374, SQ-44~SQ-46). 협력 객체는 실제 domain 클래스(RunTracker·BattleRunCoordinator)와
 * 워치 클래스(WatchSessionStore·OpponentRecordCache·PhoneLink·WatchGuidanceOutput)를 가짜 포트로 조립한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WatchFlowViewModelTest {
    private val dataLayer = FakeWearDataLayer()
    private val settings = FakeSettingsRepository()
    private val pendingRuns = FakePendingRunRepository()
    private val tokens = FakeSessionRepository()
    private val accounts = FakeAccountRepository()
    private val live = FakeLiveChannelProvider()
    private val host = FakeMeasurementHost()
    private val online = MutableStateFlow(true)
    private val battles = FakeBattleRepository().apply {
        tracks[RecordBattleKind.SELF] = listOf(selfTrack(distanceMeters = 3_000, finishSeconds = 900))
        // W31: 셀럽 목록은 구간 기록 없이 받고 고를 때 셀럽 하나로 받는다.
        tracks[RecordBattleKind.CELEBRITY] =
            listOf(celebrityListItem(id = 1, distanceMeters = 5_000, finishSeconds = 1_500))
        celebrityTracks[1] = Outcome.Success(celebrityTrack(id = 1, distanceMeters = 5_000, finishSeconds = 1_500))
    }

    private class Harness(
        val vm: WatchFlowViewModel,
        val tracker: RunTracker,
        val coordinator: BattleRunCoordinator,
        val store: WatchSessionStore,
        val guidance: WatchGuidanceOutput,
        val memory: RecordBattleMemory
    ) {
        val flow: WatchFlow get() = vm.flow.value
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        RuntimeEnvironment.getApplication().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.harness(
        isLoggedIn: Boolean = true,
        tracker: RunTracker? = null,
        shared: Harness? = null
    ): Harness {
        val clock = SchedulerClock(testScheduler)
        val runs = shared?.tracker ?: tracker
            ?: runTracker(LocalWatchGateway(alertPlayer(), settings, clock), host, settings, pendingRuns)
        val coordinator = shared?.coordinator
            ?: BattleRunCoordinator(runs, live, battles, FakeGuidanceOutput(), clock, backgroundScope)
        val cache = OpponentRecordCache(
            File(createTempDirectory("flow-cache").toFile(), "opponent-records.json"),
            battles,
            clock,
            backgroundScope,
            StandardTestDispatcher(testScheduler)
        )
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val store =
            WatchSessionStore(
                tokens,
                accounts,
                pendingRuns,
                cache,
                FakeUploadScheduler(),
                settings,
                prefs,
                backgroundScope
            )
        if (isLoggedIn) {
            backgroundScope.launch { store.accept(session()) }
            runCurrent()
        }
        val link = PhoneLink(
            dataLayer,
            HeartRateCollector(FakeHeartRateSensor(), FakeSensorPermission(), backgroundScope),
            alertPlayer(),
            clock,
            backgroundScope
        ).also { it.start() }
        val network = object : NetworkState {
            override val isOnline = online
        }
        val guidance = WatchGuidanceOutput(
            RuntimeEnvironment.getApplication(),
            FakeAlertOutput(SchedulerMonotonicClock(testScheduler)),
            clock
        )
        // 기록 대결 상대 기억은 앱 범위(싱글턴)라 다시 연 화면과 나눈다(WR13-2).
        val memory = shared?.memory ?: RecordBattleMemory()
        val battleFlow =
            WatchBattleFlow(coordinator, battles, cache, MatchWaiter(live, backgroundScope), pendingRuns, memory, clock)
        val vm = WatchFlowViewModel(runs, battleFlow, store, link, network, guidance, accounts)
        runCurrent()
        return Harness(vm, runs, coordinator, store, guidance, memory)
    }

    private fun session() = WatchSessionMessage(MEMBER, "SOCIAL", "access", "refresh", T0)

    /** 시험이 끝날 때 ViewModel 범위를 닫는다(매칭 중 1초 박자가 시험을 붙잡지 않게). */
    private fun flowTest(block: suspend TestScope.() -> Unit) = runTest {
        try {
            block()
        } finally {
            created.forEach { it.viewModelScope.cancel() }
            created.clear()
        }
    }

    private val created = mutableListOf<WatchFlowViewModel>()

    private fun TestScope.open(
        isLoggedIn: Boolean = true,
        tracker: RunTracker? = null,
        shared: Harness? = null
    ): Harness = harness(isLoggedIn, tracker, shared).also { created += it.vm }

    /** 배틀런 거리 선택에서 매칭을 요청한다. */
    private fun TestScope.requestMatch(h: Harness, meters: Long = 3_000) {
        h.vm.onModeSelected(WatchMode.BATTLE)
        h.vm.onDistanceSelected(Distance(meters))
        h.vm.onStartPressed()
        runCurrent()
    }

    private fun TestScope.phoneRunning(isRunning: Boolean) {
        val display = WatchDisplayMessage(
            isRunning = isRunning,
            elapsedSeconds = 60,
            distanceMeters = 200,
            currentPaceSecondsPerKm = 300,
            heartRateBpm = null,
            planDistanceMeters = null,
            planPaceSecondsPerKm = null,
            isHeartRateCollectionAllowed = false,
            notice = null,
            isPaused = false,
            isPausable = false
        )
        dataLayer.incoming.tryEmit(WearMessage(WearPaths.WATCH_DISPLAY, display.encode()))
        runCurrent()
    }

    /** 폰이 워치 시작 화면 표시(오늘 계획 목표, FR-042)를 보냈다. */
    private fun TestScope.phoneHome(planMeters: Long?, planPace: Int? = null) {
        val display = WatchDisplayMessage(
            isRunning = false,
            elapsedSeconds = 0,
            distanceMeters = 0,
            planDistanceMeters = planMeters,
            planPaceSecondsPerKm = planPace
        )
        dataLayer.incoming.tryEmit(WearMessage(WearPaths.WATCH_DISPLAY, display.encode()))
        runCurrent()
    }

    /** 측정 중인 대결을 조금 달린 뒤 길게 눌러 끝낸다(기록이 남는다, FR-094). */
    private fun TestScope.runAndEnd(h: Harness) {
        runNorth(h.tracker, meters = 200)
        h.vm.onEndLongPressed()
        runCurrent()
    }

    private fun recordStart(cardKind: CardKind, meters: Long) = RecordBattleStart(
        battleId = 7,
        opponentName = "상대",
        distance = Distance(meters),
        track = OpponentTrack.uniform(Distance(meters), Duration.ofSeconds(meters / 3)),
        cardKind = cardKind
    )

    /** 3·2·1·GO를 지나 측정 화면까지. */
    private fun TestScope.passCountdown() {
        advanceTimeBy(COUNTDOWN_MILLIS)
        runCurrent()
    }

    /** 북쪽으로 [meters]를 30초에 100m씩 달린다. */
    private fun TestScope.runNorth(tracker: RunTracker, meters: Int) {
        var lat = START_LAT
        tracker.onLocationSample(LocationSample(SchedulerClock(testScheduler).instant(), GeoPoint(lat, START_LNG), 5.0))
        repeat(meters / STEP_METERS) {
            advanceTimeBy(STEP_MILLIS)
            lat += STEP_DEGREES
            tracker.onLocationSample(
                LocationSample(SchedulerClock(testScheduler).instant(), GeoPoint(lat, START_LNG), 5.0)
            )
            tracker.tick(SchedulerClock(testScheduler).instant())
        }
        runCurrent()
    }

    @Test
    fun `로그인 정보가 없으면 미로그인 안내를 보이고 폰에 로그인 정보를 요청한다(FR-242)`() = flowTest {
        val h = open(isLoggedIn = false)

        assertEquals(WatchFlow.NotLoggedIn, h.flow)
        assertTrue(dataLayer.sentMessages.any { it.second == WearPaths.SESSION_REQUEST })
        h.vm.onModeSelected(WatchMode.FREE)
        assertEquals(WatchFlow.NotLoggedIn, h.flow)
    }

    @Test
    fun `로그인 정보가 오면 메인으로 바뀌고 서버 설정과 상대 기록을 맞춘다(D-379, FR-260)`() = flowTest {
        val h = open(isLoggedIn = false)
        val before = settings.refreshes

        backgroundScope.launch { h.store.accept(session()) }
        runCurrent()

        assertIs<WatchFlow.Main>(h.flow)
        assertTrue(settings.refreshes > before)
        assertTrue(RecordBattleKind.SELF in battles.calls)
    }

    @Test
    fun `메인에서 고른 모드의 거리 선택으로 가고 뒤로 가면 그 카드의 메인으로 돌아온다`() = flowTest {
        val h = open()

        h.vm.onModeSelected(WatchMode.BATTLE)
        assertIs<WatchFlow.BattleDistance>(h.flow)
        h.vm.back()
        assertEquals(WatchMode.BATTLE, (h.flow as WatchFlow.Main).mode)

        h.vm.onModeSelected(WatchMode.FREE)
        val free = h.flow as WatchFlow.FreeGoal
        assertEquals(listOf(null, Distance(3_000), Distance(5_000), Distance(10_000)), free.choices.map { it.distance })
    }

    @Test
    fun `자유런은 3 2 1 GO 뒤 측정하고 일시정지와 계속을 오가며 목표 전에 길게 눌러 끝내면 STOP 결과다(SQ-44)`() = flowTest {
        val h = open()
        h.vm.onModeSelected(WatchMode.FREE)
        h.vm.onDistanceSelected(Distance(3_000))
        h.vm.onStartPressed()
        assertEquals(WatchFlow.Countdown(WatchMode.FREE, Distance(3_000), 3), h.flow)

        passCountdown()
        val running = h.flow as WatchFlow.Running
        assertEquals(Distance(3_000), running.goal)
        assertTrue(running.isPausable)
        assertEquals(1, host.starts)

        runNorth(h.tracker, meters = 400)
        h.vm.onPause()
        runCurrent()
        assertIs<WatchFlow.Paused>(h.flow)
        h.vm.onResume()
        runCurrent()
        assertIs<WatchFlow.Running>(h.flow)

        h.vm.onEndLongPressed()
        runCurrent()
        val result = h.flow as WatchFlow.Result
        assertEquals(WatchResultHeadline.Stop, result.headline)
        assertTrue(result.summary.distance.meters > 0)
    }

    @Test
    fun `무제한 자유런을 끝내면 FINISH 결과이고 완료를 누르면 메인이다`() = flowTest {
        val h = open()
        h.vm.onModeSelected(WatchMode.FREE)
        h.vm.onStartPressed()
        passCountdown()
        runNorth(h.tracker, meters = 200)

        h.vm.onEndLongPressed()
        runCurrent()
        assertEquals(WatchResultHeadline.Finish, (h.flow as WatchFlow.Result).headline)

        h.vm.onResultClosed()
        assertIs<WatchFlow.Main>(h.flow)
    }

    @Test
    fun `측정 중 짧게 누르면 길게 눌러 종료를 잠깐 보인다(FR-248)`() = flowTest {
        val h = open()
        h.vm.onModeSelected(WatchMode.FREE)
        h.vm.onStartPressed()
        passCountdown()

        h.vm.onEndTapped()
        assertEquals(WatchFlow.Notice.HOLD_TO_END, (h.flow as WatchFlow.Running).notice)
        advanceTimeBy(WatchFlowViewModel.NOTICE_MILLIS + 100)
        runCurrent()
        assertNull((h.flow as WatchFlow.Running).notice)
    }

    @Test
    fun `서버 위치 동의가 없으면 시작하지 않고 동의 안내를 보인다(FR-245)`() = flowTest {
        settings.isLocationConsented = false
        val h = open()
        h.vm.onModeSelected(WatchMode.FREE)

        h.vm.onStartPressed()

        assertEquals(WatchFlow.Notice.LOCATION_CONSENT, (h.flow as WatchFlow.FreeGoal).notice)
        assertEquals(0, host.starts)
    }

    @Test
    fun `위치 권한 창을 거절하면 고른 화면에 위치 권한 안내를 보인다`() = flowTest {
        val h = open()
        h.vm.onModeSelected(WatchMode.FREE)

        h.vm.onLocationPermissionDenied()

        assertEquals(WatchFlow.Notice.LOCATION_PERMISSION, (h.flow as WatchFlow.FreeGoal).notice)
    }

    @Test
    fun `폰이 측정 중이면 동행 표시로 바뀌고 시작할 수 없으며 폰 측정이 끝나면 메인으로 돌아온다(FR-252, W07)`() = flowTest {
        val h = open()
        h.vm.onModeSelected(WatchMode.FREE)

        phoneRunning(true)
        assertEquals(WatchFlow.Companion, h.flow)
        h.vm.onStartPressed()
        assertEquals(WatchFlow.Companion, h.flow)
        assertEquals(0, host.starts)

        phoneRunning(false)
        assertIs<WatchFlow.Main>(h.flow)
    }

    @Test
    fun `서버에 닿지 않으면 배틀런 매칭 시작을 막고 안내한다(FR-251, W11)`() = flowTest {
        online.value = false
        val h = open()
        h.vm.onModeSelected(WatchMode.BATTLE)
        assertEquals(false, (h.flow as WatchFlow.BattleDistance).isOnline)

        h.vm.onStartPressed()

        assertEquals(WatchFlow.Notice.BATTLE_OFFLINE, (h.flow as WatchFlow.BattleDistance).notice)
        assertTrue(battles.matchRequests.isEmpty())
    }

    @Test
    fun `배틀런 매칭을 요청하고 성사되면 상대와 내 티어를 보인다(SQ-45)`() = flowTest {
        val h = open()
        h.vm.onModeSelected(WatchMode.BATTLE)
        h.vm.onDistanceSelected(Distance(3_000))

        h.vm.onStartPressed()
        runCurrent()
        val matching = h.flow as WatchFlow.Matching
        assertEquals(Distance(3_000), matching.distance)
        assertEquals("SILVER", matching.tier)
        assertEquals(listOf(Distance(3_000)), battles.matchRequests)
        advanceTimeBy(2_100)
        runCurrent()
        assertEquals(2L, (h.flow as WatchFlow.Matching).waitedSeconds)

        live.server.tryEmit(MatchFound(41, "민준", "SILVER", Distance(3_000), null, null, 3))
        runCurrent()
        val matched = h.flow as WatchFlow.Matched
        assertEquals("민준", matched.opponentName)
        assertEquals("SILVER", matched.tier)
    }

    @Test
    fun `매칭을 취소하면 서버 대기열에서 빼고 거리 선택으로 돌아간다(FR-085)`() = flowTest {
        val h = open()
        h.vm.onModeSelected(WatchMode.BATTLE)
        h.vm.onStartPressed()
        runCurrent()

        h.vm.back()
        runCurrent()

        assertIs<WatchFlow.BattleDistance>(h.flow)
        assertEquals(1, battles.cancels)
    }

    @Test
    fun `셀프런 기록 없는 거리는 누를 수 없다(W10)`() = flowTest {
        val h = open()
        h.vm.onModeSelected(WatchMode.SELF)
        val self = h.flow as WatchFlow.SelfDistance
        assertEquals(
            listOf(false, true, false, false, false),
            self.choices.map { it.isEnabled }
        )
        assertEquals(1, self.selected)

        h.vm.onDistanceSelected(Distance(1_000))

        assertEquals(self, h.flow)
    }

    @Test
    fun `셀프 기록을 받은 적 없이 오프라인이면 거리 카드가 연결 안내로 잠기고 연결되면 풀린다(W32, FR-251)`() = flowTest {
        battles.tracks.clear()
        online.value = false
        val h = open()
        h.vm.onModeSelected(WatchMode.SELF)

        val locked = h.flow as WatchFlow.SelfDistance
        assertTrue(locked.choices.all { it.isOfflineLocked && !it.isEnabled })

        h.vm.onDistanceSelected(Distance(3_000))
        assertEquals(locked, h.flow)

        online.value = true
        runCurrent()
        assertTrue((h.flow as WatchFlow.SelfDistance).choices.none { it.isOfflineLocked })
    }

    @Test
    fun `셀프 기록을 받았는데 그 거리 기록이 없으면 오프라인이어도 기록 없음이다(W32)`() = flowTest {
        val h = open()
        online.value = false
        h.vm.onModeSelected(WatchMode.SELF)

        val self = h.flow as WatchFlow.SelfDistance
        assertTrue(self.choices.none { it.isOfflineLocked })
        assertEquals(listOf(false, true, false, false, false), self.choices.map { it.isEnabled })
    }

    @Test
    fun `연결 없는 셀프런은 미리 받은 기록으로 진행하고 일시정지가 없으며 도착 전에 끝내면 STOP이다(SQ-46, I14-12)`() = flowTest {
        online.value = false
        val h = open()
        h.vm.onModeSelected(WatchMode.SELF)

        h.vm.onDistanceSelected(Distance(3_000))
        assertEquals(WatchFlow.Countdown(WatchMode.SELF, Distance(3_000), 3), h.flow)
        passCountdown()
        val running = h.flow as WatchFlow.Running
        assertEquals(false, running.isPausable)
        h.vm.onPause()
        assertIs<WatchFlow.Running>(h.flow)

        runNorth(h.tracker, meters = 300)
        h.vm.onEndLongPressed()
        runCurrent()

        assertEquals(WatchResultHeadline.Stop, (h.flow as WatchFlow.Result).headline)
        assertTrue(battles.forfeits.isEmpty())
    }

    @Test
    fun `연결된 셀프런은 서버가 대결을 열 때까지 연결 중을 보이고 답이 오면 3부터 센다`() = flowTest {
        val gate = CompletableDeferred<Unit>()
        battles.recordStartGate = gate
        battles.recordStart = recordStart(CardKind.SELF_RUN, meters = 3_000)
        val h = open()
        h.vm.onModeSelected(WatchMode.SELF)

        h.vm.onDistanceSelected(Distance(3_000))
        runCurrent()
        assertEquals(WatchFlow.Countdown(WatchMode.SELF, Distance(3_000), 3, isConnecting = true), h.flow)

        gate.complete(Unit)
        runCurrent()
        assertEquals(WatchFlow.Countdown(WatchMode.SELF, Distance(3_000), 3), h.flow)
    }

    @Test
    fun `셀럽런은 거리 다음 셀럽 목록을 보이고 고르면 카운트다운이다(W09)`() = flowTest {
        val h = open()
        h.vm.onModeSelected(WatchMode.CELEB)
        val celeb = h.flow as WatchFlow.CelebDistance
        assertEquals(listOf(1, 0, 0, 0), celeb.choices.map { it.count })

        h.vm.onDistanceSelected(Distance(5_000))
        val list = h.flow as WatchFlow.CelebList
        assertEquals("셀럽1", list.celebrities.single().displayName)
        h.vm.back()
        assertIs<WatchFlow.CelebDistance>(h.flow)

        h.vm.onDistanceSelected(Distance(5_000))
        h.vm.onCelebritySelected((h.flow as WatchFlow.CelebList).celebrities.single())
        runCurrent()
        assertEquals(WatchFlow.Countdown(WatchMode.CELEB, Distance(5_000), 3), h.flow)
    }

    /** 셀럽런 5km 목록을 연다. */
    private fun openCelebList(h: Harness): WatchFlow.CelebList {
        h.vm.onModeSelected(WatchMode.CELEB)
        h.vm.onDistanceSelected(Distance(5_000))
        return h.flow as WatchFlow.CelebList
    }

    @Test
    fun `셀럽을 고르면 받는 동안 그 항목을 받는 중으로 보이고 다시 눌러도 한 번만 받은 뒤 카운트다운이다(W31)`() = flowTest {
        val gate = CompletableDeferred<Unit>()
        battles.celebrityGate = gate
        val h = open()
        val celebrity = openCelebList(h).celebrities.single()

        h.vm.onCelebritySelected(celebrity)
        runCurrent()
        assertEquals(celebrity, (h.flow as WatchFlow.CelebList).fetching)
        h.vm.onCelebritySelected(celebrity)
        runCurrent()

        gate.complete(Unit)
        runCurrent()
        assertEquals(WatchFlow.Countdown(WatchMode.CELEB, Distance(5_000), 3), h.flow)
        assertEquals(listOf(1L), battles.celebrityTrackCalls)
    }

    @Test
    fun `한 번 받은 셀럽은 다음에 연결 없이 고를 수 있다(W31)`() = flowTest {
        val h = open()
        h.vm.onCelebritySelected(openCelebList(h).celebrities.single())
        runCurrent()
        passCountdown()
        runAndEnd(h)
        h.vm.onResultClosed()
        runCurrent()

        online.value = false
        h.vm.onCelebritySelected(openCelebList(h).celebrities.single())

        assertEquals(WatchFlow.Countdown(WatchMode.CELEB, Distance(5_000), 3), h.flow)
        assertEquals(listOf(1L), battles.celebrityTrackCalls)
    }

    @Test
    fun `연결이 없고 받아 둔 구간 기록이 없으면 받지 않고 연결 안내를 보인다(W31)`() = flowTest {
        online.value = false
        val h = open()

        h.vm.onCelebritySelected(openCelebList(h).celebrities.single())
        runCurrent()

        val list = h.flow as WatchFlow.CelebList
        assertEquals(WatchFlow.Notice.CELEB_OFFLINE, list.notice)
        assertEquals(null, list.fetching)
        assertTrue(battles.celebrityTrackCalls.isEmpty())
    }

    @Test
    fun `셀럽 구간 기록이 없으면(404) 고를 수 없는 기록이고 받다가 끊기면 연결 안내다(W31)`() = flowTest {
        battles.celebrityTracks[1] = Outcome.Failure(AppError.Rejected("CELEBRITY_REMOVED"))
        val h = open()
        val celebrity = openCelebList(h).celebrities.single()

        h.vm.onCelebritySelected(celebrity)
        runCurrent()
        assertEquals(WatchFlow.Notice.CELEBRITY_REMOVED, (h.flow as WatchFlow.CelebList).notice)
        assertEquals(null, (h.flow as WatchFlow.CelebList).fetching)

        battles.celebrityTracks.remove(1)
        h.vm.onCelebritySelected(celebrity)
        runCurrent()
        assertEquals(WatchFlow.Notice.CELEB_OFFLINE, (h.flow as WatchFlow.CelebList).notice)

        battles.celebrityTracks[1] = Outcome.Failure(AppError.Server(503))
        h.vm.onCelebritySelected(celebrity)
        runCurrent()
        assertEquals(WatchFlow.Notice.RETRY_LATER, (h.flow as WatchFlow.CelebList).notice)
    }

    @Test
    fun `셀럽 구간 기록을 받는 동안 뒤로 가면 받은 뒤에도 카운트다운으로 가지 않는다(W31)`() = flowTest {
        val gate = CompletableDeferred<Unit>()
        battles.celebrityGate = gate
        val h = open()
        h.vm.onCelebritySelected(openCelebList(h).celebrities.single())
        runCurrent()

        h.vm.back()
        gate.complete(Unit)
        runCurrent()

        assertIs<WatchFlow.CelebDistance>(h.flow)
        assertFalse(h.tracker.isMeasuring)
    }

    @Test
    fun `뒤로 갔다가 다시 들어와 같은 셀럽을 고르면 새로 받은 뒤 카운트다운이다(W31)`() = flowTest {
        val gate = CompletableDeferred<Unit>()
        battles.celebrityGate = gate
        val h = open()
        h.vm.onCelebritySelected(openCelebList(h).celebrities.single())
        runCurrent()
        h.vm.back()

        h.vm.onDistanceSelected(Distance(5_000))
        h.vm.onCelebritySelected((h.flow as WatchFlow.CelebList).celebrities.single())
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(WatchFlow.Countdown(WatchMode.CELEB, Distance(5_000), 3), h.flow)
        assertEquals(listOf(1L, 1L), battles.celebrityTrackCalls)
    }

    @Test
    fun `측정 서비스가 기록 없이 측정을 끝내면 측정 중단 안내와 메인으로 간다`() = flowTest {
        val h = open()
        h.vm.onModeSelected(WatchMode.FREE)
        h.vm.onStartPressed()
        passCountdown()

        h.tracker.endRun()
        runCurrent()

        assertEquals(WatchFlow.Notice.MEASUREMENT_STOPPED, (h.flow as WatchFlow.Main).notice)
    }

    @Test
    fun `거리 0으로 길게 눌러 끝내면 저장하지 않았다는 안내와 메인으로 가고 안내는 잠시 뒤 사라진다(R13 D4)`() = flowTest {
        val h = open()
        h.vm.onModeSelected(WatchMode.FREE)
        h.vm.onStartPressed()
        passCountdown()

        h.vm.onEndLongPressed()
        runCurrent()

        assertEquals(WatchFlow.Notice.NOT_SAVED, (h.flow as WatchFlow.Main).notice)
        assertTrue(pendingRuns.saved.isEmpty())
        advanceTimeBy(WatchFlowViewModel.NOTICE_MILLIS + 1)
        runCurrent()
        assertNull((h.flow as WatchFlow.Main).notice)
    }

    @Test
    fun `측정 중 받은 로그아웃은 결과를 닫을 때 지우고 미로그인 안내로 간다(FR-241, D-389)`() = flowTest {
        val h = open()
        h.vm.onModeSelected(WatchMode.FREE)
        h.vm.onStartPressed()
        passCountdown()
        runNorth(h.tracker, meters = 200)
        backgroundScope.launch { h.store.deferRevoke(SessionRevokedMessage(MEMBER, T0 + 1)) }
        runCurrent()
        assertIs<WatchSession.RevokePending>(h.store.state.value)

        h.vm.onEndLongPressed()
        runCurrent()
        assertIs<WatchFlow.Result>(h.flow)
        h.vm.onResultClosed()
        runCurrent()

        assertEquals(WatchFlow.NotLoggedIn, h.flow)
        assertEquals(WatchSession.None, h.store.state.value)
        assertNull(tokens.tokens)
    }

    @Test
    fun `워치가 측정 중에 앱을 다시 열면 측정 화면으로 연다`() = flowTest {
        val first = open()
        first.vm.onModeSelected(WatchMode.FREE)
        first.vm.onDistanceSelected(Distance(5_000))
        first.vm.onStartPressed()
        passCountdown()

        val reopened = open(tracker = first.tracker)

        val running = reopened.flow as WatchFlow.Running
        assertEquals(Distance(5_000), running.goal)
        assertTrue(running.isPausable)
    }

    @Test
    fun `자유런 첫 카드는 자유런 설정의 목표 거리다(FR-244, FR-048)`() = flowTest {
        settings.guidance = GuidanceSettings.DEFAULT.copy(freeRunTarget = Distance(5_000))
        val h = open()

        h.vm.onModeSelected(WatchMode.FREE)

        val free = h.flow as WatchFlow.FreeGoal
        assertEquals(listOf(null, Distance(3_000), Distance(5_000), Distance(10_000)), free.choices.map { it.distance })
        assertEquals(Distance(5_000), free.current.distance)
    }

    @Test
    fun `코치 계획 따르기가 켜져 있고 오늘 계획이 있으면 첫 카드는 계획 거리다(FR-244)`() = flowTest {
        settings.guidance =
            GuidanceSettings.DEFAULT.copy(isFollowingCoachPlan = true, freeRunTarget = Distance(10_000))
        val h = open()
        phoneHome(planMeters = 3_000)

        h.vm.onModeSelected(WatchMode.FREE)

        val free = h.flow as WatchFlow.FreeGoal
        assertEquals(4, free.choices.size)
        assertEquals(Distance(3_000), free.current.distance)
    }

    @Test
    fun `계획 거리가 네 카드에 없으면 그 거리 카드를 하나 더 두고 처음 고른다(FR-244 7km)`() = flowTest {
        settings.guidance = GuidanceSettings.DEFAULT.copy(isFollowingCoachPlan = true)
        val h = open()
        phoneHome(planMeters = 7_000, planPace = 360)

        h.vm.onModeSelected(WatchMode.FREE)
        val free = h.flow as WatchFlow.FreeGoal
        assertEquals(
            listOf(null, Distance(3_000), Distance(5_000), Distance(7_000), Distance(10_000)),
            free.choices.map { it.distance }
        )
        assertEquals(Distance(7_000), free.current.distance)

        h.vm.onStartPressed()
        assertEquals(WatchFlow.Countdown(WatchMode.FREE, Distance(7_000), 3), h.flow)
        passCountdown()
        assertEquals(Distance(7_000), (h.flow as WatchFlow.Running).goal)
    }

    @Test
    fun `코치 계획 따르기가 꺼져 있으면 폰의 계획을 두고 자유런 설정의 목표 거리를 처음 고른다(FR-048)`() = flowTest {
        settings.guidance = GuidanceSettings.DEFAULT.copy(freeRunTarget = Distance(10_000))
        val h = open()
        phoneHome(planMeters = 7_000)

        h.vm.onModeSelected(WatchMode.FREE)

        val free = h.flow as WatchFlow.FreeGoal
        assertEquals(4, free.choices.size)
        assertEquals(Distance(10_000), free.current.distance)
    }

    @Test
    fun `배틀런 첫 카드는 기본 대결 거리이고 읽지 못하면 5km다(FR-257, FR-109)`() = flowTest {
        val first = open()
        first.vm.onModeSelected(WatchMode.BATTLE)
        assertEquals(Distance(5_000), (first.flow as WatchFlow.BattleDistance).current.distance)

        battles.matchStatusAnswer =
            Outcome.Success(MatchAcceptance(isWaiting = false, distance = null, defaultDistance = Distance(3_000)))
        val second = open()
        second.vm.onModeSelected(WatchMode.BATTLE)
        assertEquals(Distance(3_000), (second.flow as WatchFlow.BattleDistance).current.distance)
    }

    @Test
    fun `매칭을 요청한 거리가 다음 배틀런의 첫 카드다(FR-109)`() = flowTest {
        val h = open()
        h.vm.onModeSelected(WatchMode.BATTLE)
        h.vm.onDistanceSelected(Distance(10_000))
        h.vm.onStartPressed()
        runCurrent()
        h.vm.back()
        runCurrent()
        h.vm.back()

        h.vm.onModeSelected(WatchMode.BATTLE)

        assertEquals(Distance(10_000), (h.flow as WatchFlow.BattleDistance).current.distance)
    }

    @Test
    fun `워치에서 시작한 배틀런은 시작 기기 WATCH로 기록한다(FR-245, D-378)`() = flowTest {
        val h = open()
        h.vm.onModeSelected(WatchMode.BATTLE)
        h.vm.onDistanceSelected(Distance(1_000))
        h.vm.onStartPressed()
        runCurrent()
        live.server.tryEmit(MatchFound(41, "민준", "SILVER", Distance(1_000), null, null, 3))
        runCurrent()
        val startAt = SchedulerClock(testScheduler).instant().plusSeconds(5)
        live.server.tryEmit(BattleStart(41, startAt, opponentTrack = null))
        runCurrent()
        advanceTimeBy(5_000 + WatchFlowViewModel.GO_MILLIS + 300)
        runCurrent()
        assertIs<WatchFlow.Running>(h.flow)

        runAndEnd(h)

        val run = pendingRuns.saved.single()
        assertEquals(41L, run.battleId)
        assertEquals(RunStartSpec.StartDevice.WATCH, run.startDevice)
    }

    @Test
    fun `워치에서 서버 대결로 시작한 셀프런과 셀럽런은 시작 기기 WATCH로 기록한다(FR-245, D-378)`() = flowTest {
        val h = open()
        battles.recordStart = recordStart(CardKind.SELF_RUN, meters = 3_000)
        h.vm.onModeSelected(WatchMode.SELF)
        h.vm.onDistanceSelected(Distance(3_000))
        runCurrent()
        passCountdown()
        assertIs<WatchFlow.Running>(h.flow)
        runAndEnd(h)
        h.vm.onResultClosed()

        battles.recordStart = recordStart(CardKind.CELEB_RUN, meters = 5_000)
        h.vm.onModeSelected(WatchMode.CELEB)
        h.vm.onDistanceSelected(Distance(5_000))
        h.vm.onCelebritySelected((h.flow as WatchFlow.CelebList).celebrities.single())
        runCurrent()
        passCountdown()
        assertIs<WatchFlow.Running>(h.flow)
        runAndEnd(h)

        assertEquals(listOf(CardKind.SELF_RUN, CardKind.CELEB_RUN), pendingRuns.saved.map { it.cardKind })
        assertEquals(List(2) { RunStartSpec.StartDevice.WATCH }, pendingRuns.saved.map { it.startDevice })
        assertEquals(listOf(7L, 7L), battles.forfeits)
    }

    /** 서버 대결 7번의 판정(셀프·셀럽 공통). */
    private fun verdict(verdict: String, mine: Long?, theirs: Long) = BattleResult(
        battleId = 7,
        isPending = false,
        verdict = verdict,
        rows = emptyList(),
        rewardPoints = null,
        isNewRecord = false,
        opponentName = "상대",
        myReach = mine?.let { Duration.ofSeconds(it) },
        opponentReach = Duration.ofSeconds(theirs)
    )

    /** 서버 대결로 셀프런 3km를 시작해 측정 화면까지 간다. */
    private fun TestScope.startOnlineSelf(h: Harness) {
        battles.recordStart = recordStart(CardKind.SELF_RUN, meters = 3_000)
        h.vm.onModeSelected(WatchMode.SELF)
        h.vm.onDistanceSelected(Distance(3_000))
        runCurrent()
        passCountdown()
        assertIs<WatchFlow.Running>(h.flow)
    }

    private val Harness.headline: WatchResultHeadline get() = (flow as WatchFlow.Result).headline

    @Test
    fun `서버 대결 셀프런은 판정을 기다리는 동안 보류이고 판정이 오면 워치 계산이 아니라 서버 결과다(W27, WR12-4)`() = flowTest {
        val h = open()
        startOnlineSelf(h)

        // 도착 전에 끝냈으니 워치 계산이면 STOP이다. 서버는 BEST(20초 단축)로 판정한다.
        runAndEnd(h)
        assertEquals(WatchResultHeadline.Pending, h.headline)
        battles.results[7] = verdict("WIN", mine = 880, theirs = 900)
        advanceTimeBy(WatchFlowViewModel.RESULT_RETRY_MILLIS)
        runCurrent()

        assertEquals(WatchResultHeadline.Best(20), h.headline)
    }

    @Test
    fun `서버 대결 셀럽런은 서버 판정 LOSE를 고른 셀럽 표시명과 도달 시간 차로 보인다(W27)`() = flowTest {
        battles.results[7] = verdict("LOSE", mine = 1_600, theirs = 1_500)
        val h = open()
        battles.recordStart = recordStart(CardKind.CELEB_RUN, meters = 5_000)
        h.vm.onModeSelected(WatchMode.CELEB)
        h.vm.onDistanceSelected(Distance(5_000))
        h.vm.onCelebritySelected((h.flow as WatchFlow.CelebList).celebrities.single())
        runCurrent()
        passCountdown()

        runAndEnd(h)

        assertEquals(WatchResultHeadline.Lose("셀럽1", 100), h.headline)
    }

    @Test
    fun `서버 판정을 정해진 횟수 안에 받지 못하면 보류를 거두고 워치 계산으로 보인다(W27)`() = flowTest {
        val h = open()
        startOnlineSelf(h)
        runAndEnd(h)

        repeat(WatchFlowViewModel.RECORD_RESULT_ATTEMPTS - 1) {
            assertEquals(WatchResultHeadline.Pending, h.headline)
            advanceTimeBy(WatchFlowViewModel.RESULT_RETRY_MILLIS)
            runCurrent()
        }

        assertEquals(WatchResultHeadline.Stop, h.headline)
        advanceTimeBy(WatchFlowViewModel.RESULT_RETRY_MILLIS * 2)
        runCurrent()
        assertEquals(WatchResultHeadline.Stop, h.headline)
    }

    @Test
    fun `서버가 계속 보류로 답해도 정해진 횟수 뒤 워치 계산이고 보류 중 완료로 닫으면 늦은 판정이 메인을 덮지 않는다(W27)`() = flowTest {
        val h = open()
        startOnlineSelf(h)
        battles.results[7] = verdict("WIN", mine = 880, theirs = 900).copy(isPending = true, verdict = null)
        runAndEnd(h)
        advanceTimeBy(WatchFlowViewModel.RESULT_RETRY_MILLIS * (WatchFlowViewModel.RECORD_RESULT_ATTEMPTS - 1))
        runCurrent()
        assertEquals(WatchResultHeadline.Stop, h.headline)
        h.vm.onResultClosed()
        runCurrent()

        startOnlineSelf(h)
        runAndEnd(h)
        assertEquals(WatchResultHeadline.Pending, h.headline)
        h.vm.onResultClosed()
        runCurrent()
        battles.results[7] = verdict("WIN", mine = 880, theirs = 900)
        advanceTimeBy(WatchFlowViewModel.RESULT_RETRY_MILLIS * 2)
        runCurrent()

        assertIs<WatchFlow.Main>(h.flow)
    }

    @Test
    fun `서버 셀프런을 기록보다 늦게 완주하면 판정을 기다리는 동안 다른 사건이 와도 결과가 남고 서버가 도달 시간 없이 LOSE를 줘도 GOOD이다(R13 D1)`() = flowTest {
        // 셀프 기록 3km 14분 18초(858초), 나는 15분(900초)에 들어온다. 기록이 먼저 도착해 서버 시계가 대결을 닫은 뒤(RESULT_PENDING)
        // 기록이 올라가고, 서버는 카운트다운 뒤에 도착한 기록이라 LOSE로 판정하며 내 도달 시간을 주지 않는다(myReach 없음).
        battles.tracks[RecordBattleKind.SELF] = listOf(selfTrack(distanceMeters = 3_000, finishSeconds = 858))
        battles.results[7] = verdict("WIN", mine = 0, theirs = 0).copy(isPending = true, verdict = null)
        val h = open()
        startOnlineSelf(h)
        runNorth(h.tracker, meters = 3_000)
        advanceTimeBy(WatchFlowViewModel.OUTCOME_WAIT_MILLIS)
        runCurrent()
        assertEquals(WatchResultHeadline.Pending, h.headline)

        // 판정을 기다리는 동안: 로그인 정보 다시 받음, 폰 측정 시작과 끝, 조정자의 다음 단계(Countdown, Discarded, Idle).
        backgroundScope.launch { h.store.accept(session()) }
        phoneRunning(true)
        phoneRunning(false)
        h.coordinator.onOfflineRecordBattle(
            OfflineRecordBattle(
                RecordBattleKind.SELF,
                Distance(1_000),
                celebrityId = null,
                track = OpponentTrack.uniform(Distance(1_000), Duration.ofSeconds(300))
            )
        )
        runCurrent()
        h.coordinator.cancel()
        runCurrent()
        h.coordinator.acknowledgeFinished()
        runCurrent()
        assertEquals(WatchResultHeadline.Pending, h.headline)

        battles.results[7] = verdict("LOSE", mine = null, theirs = 858)
        advanceTimeBy(WatchFlowViewModel.RESULT_RETRY_MILLIS)
        runCurrent()
        val result = h.flow as WatchFlow.Result
        assertTrue(result.summary.distance.meters >= 3_000)
        val good = WatchResultHeadline.Good(result.summary.elapsed.seconds - 858)
        assertEquals(good, result.headline)
        assertTrue(good.gapSeconds!! in 42L..43L)

        // 완료를 누를 때까지 결과에 머문다.
        advanceTimeBy(WatchFlowViewModel.RESULT_RETRY_MILLIS * WatchFlowViewModel.RECORD_RESULT_ATTEMPTS)
        runCurrent()
        assertEquals(good, h.headline)
        h.vm.onResultClosed()
        assertIs<WatchFlow.Main>(h.flow)
    }

    @Test
    fun `서버 셀프런을 도착 전에 끝내고 서버가 도달 시간 없이 판정하면 STOP이다(R13 D1)`() = flowTest {
        battles.results[7] = verdict("LOSE", mine = null, theirs = 900)
        val h = open()
        startOnlineSelf(h)

        runAndEnd(h)

        assertEquals(WatchResultHeadline.Stop, h.headline)
    }

    @Test
    fun `연결 없이 끝까지 달린 셀프런은 워치 계산이고 최고 기록과 같은 초면 진 것이라 GOOD이다(W27, W28)`() = flowTest {
        online.value = false
        battles.tracks[RecordBattleKind.SELF] = listOf(selfTrack(distanceMeters = 3_000, finishSeconds = 2_000))
        val h = open()
        h.vm.onModeSelected(WatchMode.SELF)
        h.vm.onDistanceSelected(Distance(3_000))
        passCountdown()
        runNorth(h.tracker, meters = 3_000)
        val first = h.flow as WatchFlow.Result
        assertIs<WatchResultHeadline.Best>(first.headline)

        // 서버 최고 기록이 첫 회 완주 시간(초 단위)이 되었고, 둘째 회를 같은 페이스로 달린다.
        battles.tracks[RecordBattleKind.SELF] =
            listOf(selfTrack(distanceMeters = 3_000, finishSeconds = first.summary.elapsed.seconds))
        h.vm.onResultClosed()
        runCurrent()
        h.vm.onModeSelected(WatchMode.SELF)
        h.vm.onDistanceSelected(Distance(3_000))
        passCountdown()
        runNorth(h.tracker, meters = 3_000)

        assertEquals(WatchResultHeadline.Good(0), h.headline)
    }

    @Test
    fun `언랭이면 매칭을 요청하기 전에 잠그고 남은 러닝 횟수를 알린다(WR12-1 PV-1, FR-080)`() = flowTest {
        accounts.summary = MySummary("러너", "UNRANKED", 1, null, null, validRuns = 1)
        val h = open()

        requestMatch(h)

        val picker = h.flow as WatchFlow.BattleDistance
        assertEquals(WatchFlow.Notice.RANDOM_LOCKED, picker.notice)
        assertEquals(2, picker.runsLeft)
        assertTrue(battles.matchRequests.isEmpty())
        assertEquals(0, live.opened)
    }

    @Test
    fun `언랭인데 유효 러닝 수를 모르면 남은 수 없이 잠근다`() = flowTest {
        accounts.summary = MySummary("러너", "UNRANKED", 1, null, null, validRuns = null)
        val h = open()

        h.vm.onModeSelected(WatchMode.BATTLE)

        assertEquals(0, (h.flow as WatchFlow.BattleDistance).runsLeft)
        h.vm.onStartPressed()
        assertEquals(WatchFlow.Notice.RANDOM_LOCKED, (h.flow as WatchFlow.BattleDistance).notice)
        assertTrue(battles.matchRequests.isEmpty())
    }

    @Test
    fun `서버가 RANKED_ONLY로 거부하면 매칭 실패가 아니라 배틀런 잠금 안내다(WR12-1 PV-1)`() = flowTest {
        battles.matchAnswer = Outcome.Failure(AppError.Rejected("RANKED_ONLY"))
        val h = open()

        requestMatch(h)

        val picker = h.flow as WatchFlow.BattleDistance
        assertEquals(WatchFlow.Notice.RANDOM_LOCKED, picker.notice)
        assertEquals(0, picker.runsLeft)
    }

    @Test
    fun `서버 매칭 거부 사유마다 폰과 같은 뜻의 안내를 보인다(WR12-1)`() = flowTest {
        val h = open()
        val expected = mapOf(
            "ALREADY_IN_BATTLE" to WatchFlow.Notice.ALREADY_IN_BATTLE,
            "ALREADY_MATCHED" to WatchFlow.Notice.ALREADY_IN_BATTLE,
            "QUEUE_UNAVAILABLE" to WatchFlow.Notice.QUEUE_UNAVAILABLE,
            "BATTLE_DISTANCE_INVALID" to WatchFlow.Notice.DISTANCE_INVALID,
            "LOCATION_CONSENT_REQUIRED" to WatchFlow.Notice.LOCATION_CONSENT,
            "MATCH_FAILED" to WatchFlow.Notice.MATCH_FAILED
        )

        val shown = expected.keys.associateWith { code ->
            battles.matchAnswer = Outcome.Failure(AppError.Rejected(code))
            h.vm.back()
            requestMatch(h)
            (h.flow as WatchFlow.BattleDistance).notice
        }

        assertEquals(expected, shown)
    }

    @Test
    fun `매칭 대기 중 서버가 사유 코드로 끝내면 그 사유의 안내를 보인다(WR12-1, awaitMatch)`() = flowTest {
        val h = open()
        requestMatch(h)
        assertIs<WatchFlow.Matching>(h.flow)

        live.server.tryEmit(MatchFailed("QUEUE_UNAVAILABLE", "대기열 없음"))
        runCurrent()

        assertEquals(WatchFlow.Notice.QUEUE_UNAVAILABLE, (h.flow as WatchFlow.BattleDistance).notice)
    }

    @Test
    fun `셀프런 서버 시작이 NO_RECORD로 거부되면 기록 없음 안내를 보인다(WR12-1)`() = flowTest {
        battles.recordStartFailure = AppError.Rejected("NO_RECORD")
        val h = open()
        h.vm.onModeSelected(WatchMode.SELF)

        h.vm.onDistanceSelected(Distance(3_000))
        runCurrent()

        assertEquals(WatchFlow.Notice.NO_RECORD, (h.flow as WatchFlow.SelfDistance).notice)
    }

    @Test
    fun `셀럽런 서버 시작이 CELEBRITY_REMOVED로 거부되면 고를 수 없는 기록 안내를 보인다(WR12-1)`() = flowTest {
        battles.recordStartFailure = AppError.Rejected("CELEBRITY_REMOVED")
        val h = open()
        h.vm.onModeSelected(WatchMode.CELEB)
        h.vm.onDistanceSelected(Distance(5_000))

        h.vm.onCelebritySelected((h.flow as WatchFlow.CelebList).celebrities.single())
        runCurrent()

        assertEquals(WatchFlow.Notice.CELEBRITY_REMOVED, (h.flow as WatchFlow.CelebList).notice)
    }

    @Test
    fun `대결 성사 뒤 출발 전에 앱을 다시 열어도 매칭 완료 화면과 대결을 이어 간다(WR12-2)`() = flowTest {
        val first = open()
        requestMatch(first)
        live.server.tryEmit(MatchFound(41, "민준", "SILVER", Distance(3_000), null, null, 3))
        runCurrent()
        assertIs<WatchFlow.Matched>(first.flow)

        val reopened = open(shared = first)
        assertEquals("민준", (reopened.flow as WatchFlow.Matched).opponentName)

        val startAt = SchedulerClock(testScheduler).instant().plusSeconds(5)
        live.server.tryEmit(BattleStart(41, startAt, opponentTrack = null))
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        assertIs<WatchFlow.Countdown>(reopened.flow)
        advanceTimeBy(2_000 + WatchFlowViewModel.GO_MILLIS + 300)
        runCurrent()

        val running = reopened.flow as WatchFlow.Running
        assertEquals(WatchMode.BATTLE, running.mode)
        assertEquals(Distance(3_000), running.goal)
    }

    @Test
    fun `대결 카운트다운 중에 앱을 다시 열면 카운트다운으로 연다(WR12-2)`() = flowTest {
        val first = open()
        requestMatch(first)
        live.server.tryEmit(MatchFound(41, "민준", "SILVER", Distance(3_000), null, null, 3))
        runCurrent()
        val startAt = SchedulerClock(testScheduler).instant().plusSeconds(2)
        live.server.tryEmit(BattleStart(41, startAt, opponentTrack = null))
        runCurrent()

        val reopened = open(shared = first)

        val countdown = reopened.flow as WatchFlow.Countdown
        assertEquals(WatchMode.BATTLE, countdown.mode)
        assertEquals(Distance(3_000), countdown.distance)
    }

    @Test
    fun `카운트다운 측정 결과에서는 뒤로 가기를 받지 않고 결과는 완료로만 닫힌다(상태도 2_4_25, WR12-2)`() = flowTest {
        val h = open()
        h.vm.onModeSelected(WatchMode.FREE)
        h.vm.onStartPressed()
        h.vm.back()
        assertIs<WatchFlow.Countdown>(h.flow)

        passCountdown()
        h.vm.back()
        assertIs<WatchFlow.Running>(h.flow)

        runNorth(h.tracker, meters = 200)
        h.vm.onEndLongPressed()
        runCurrent()
        h.vm.back()
        assertIs<WatchFlow.Result>(h.flow)
    }

    @Test
    fun `결과를 완료로 닫으면 안내 음성 엔진을 닫는다(WR12-6, NFR-048)`() = flowTest {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT, true)
        ShadowTextToSpeech.addLanguageAvailability(Locale.KOREAN)
        val h = open()
        h.vm.onModeSelected(WatchMode.FREE)
        h.vm.onStartPressed()
        passCountdown()
        h.guidance.play(
            Guidance(Guidance.Kind.SPLIT, GuidanceMessage.SPLIT, listOf("1"), hasVoice = true, hasVibration = false)
        )
        shadowOf(Looper.getMainLooper()).idle()
        val tts = shadowOf(checkNotNull(ShadowTextToSpeech.getLastTextToSpeechInstance()))
        runNorth(h.tracker, meters = 200)
        h.vm.onEndLongPressed()
        runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(tts.isShutdown)

        h.vm.onResultClosed()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(tts.isShutdown)
    }

    @Test
    fun `결과 없이 측정이 끝나도 안내 음성 엔진을 닫는다(WR13-3, NFR-048)`() = flowTest {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT, true)
        ShadowTextToSpeech.addLanguageAvailability(Locale.KOREAN)
        val h = open()
        h.vm.onModeSelected(WatchMode.FREE)
        h.vm.onStartPressed()
        passCountdown()
        h.guidance.play(
            Guidance(Guidance.Kind.SPLIT, GuidanceMessage.SPLIT, listOf("1"), hasVoice = true, hasVibration = false)
        )
        shadowOf(Looper.getMainLooper()).idle()
        val tts = shadowOf(checkNotNull(ShadowTextToSpeech.getLastTextToSpeechInstance()))

        // 측정 서비스가 기록 없이 끝냈다(운동 세션 실패): 결과 화면 없이 메인으로 간다.
        h.tracker.endRun()
        runCurrent()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(WatchFlow.Notice.MEASUREMENT_STOPPED, (h.flow as WatchFlow.Main).notice)
        assertTrue(tts.isShutdown)
    }

    @Test
    fun `결과 화면에서 화면이 사라지면 도착 안내를 끊지 않게 음성 엔진을 닫지 않는다(WR13-3)`() = flowTest {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT, true)
        ShadowTextToSpeech.addLanguageAvailability(Locale.KOREAN)
        val h = open()
        h.vm.onModeSelected(WatchMode.FREE)
        h.vm.onStartPressed()
        passCountdown()
        h.guidance.play(
            Guidance(Guidance.Kind.SPLIT, GuidanceMessage.SPLIT, listOf("1"), hasVoice = true, hasVibration = false)
        )
        shadowOf(Looper.getMainLooper()).idle()
        val tts = shadowOf(checkNotNull(ShadowTextToSpeech.getLastTextToSpeechInstance()))
        runNorth(h.tracker, meters = 200)
        h.vm.onEndLongPressed()
        runCurrent()
        assertIs<WatchFlow.Result>(h.flow)

        clearViewModel(h.vm)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(tts.isShutdown)
    }

    /** 화면(액티비티)이 끝나 ViewModel이 지워진다(onCleared). */
    private fun clearViewModel(vm: WatchFlowViewModel) {
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
        }
        ViewModelProvider(store, factory)[WatchFlowViewModel::class.java]
        store.clear()
    }

    @Test
    fun `미로그인 동안 폰 연결이 새로 닿으면 로그인 정보를 다시 요청한다(WR13-1, D-404)`() = flowTest {
        dataLayer.nodes = emptyList()
        val h = open(isLoggedIn = false)
        assertEquals(WatchFlow.NotLoggedIn, h.flow)
        assertEquals(0, sessionRequests())

        dataLayer.nodes = listOf("phone")
        advanceTimeBy(CONNECTION_POLL_MILLIS)
        runCurrent()

        assertEquals(1, sessionRequests())
    }

    @Test
    fun `미로그인 화면의 로그인 정보 요청은 간격 안에서는 한 번이고 간격이 지나 다시 닿으면 또 보낸다(WR13-1)`() = flowTest {
        open(isLoggedIn = false)
        // 앱 시작(세션 None)과 미로그인 화면 진입이 겹쳐도 한 번이다.
        assertEquals(1, sessionRequests())

        reconnectPhone()
        assertEquals(1, sessionRequests())

        advanceTimeBy(WatchFlowViewModel.SESSION_REQUEST_GAP_MILLIS)
        reconnectPhone()
        assertEquals(2, sessionRequests())
    }

    @Test
    fun `연결 없는 셀프런 측정 중에 앱을 다시 열면 셀프런으로 이어 가고 도착 전에 끝내면 STOP이다(WR13-2)`() = flowTest {
        online.value = false
        val first = open()
        first.vm.onModeSelected(WatchMode.SELF)
        first.vm.onDistanceSelected(Distance(3_000))
        passCountdown()
        assertIs<WatchFlow.Running>(first.flow)

        val reopened = open(shared = first)
        val running = reopened.flow as WatchFlow.Running
        assertEquals(WatchMode.SELF, running.mode)
        assertFalse(running.isPausable)

        runNorth(reopened.tracker, meters = 300)
        reopened.vm.onEndLongPressed()
        runCurrent()

        // 배틀런으로 되살리면 오프라인 대결 ID 0이라 자유런 FINISH였다.
        assertEquals(WatchResultHeadline.Stop, reopened.headline)
    }

    @Test
    fun `서버 셀럽런 카운트다운 중에 앱을 다시 열면 셀럽런으로 이어 가고 결과는 셀럽 판정이다(WR13-2)`() = flowTest {
        battles.results[7] = verdict("LOSE", mine = 1_600, theirs = 1_500)
        val first = open()
        battles.recordStart = recordStart(CardKind.CELEB_RUN, meters = 5_000)
        first.vm.onModeSelected(WatchMode.CELEB)
        first.vm.onDistanceSelected(Distance(5_000))
        first.vm.onCelebritySelected((first.flow as WatchFlow.CelebList).celebrities.single())
        runCurrent()

        val reopened = open(shared = first)
        assertEquals(WatchFlow.Countdown(WatchMode.CELEB, Distance(5_000), 3), reopened.flow)
        passCountdown()
        assertEquals(WatchMode.CELEB, (reopened.flow as WatchFlow.Running).mode)
        runAndEnd(reopened)

        assertEquals(WatchResultHeadline.Lose("셀럽1", 100), reopened.headline)
    }

    /** TC-5480(실패 갈래 1, M89): 서버에 닿지 못하면(Network) 연결 중에서 오프라인 대결로 바꿔 3부터 센다. */
    @Test
    fun `연결 중 서버 대결을 열지 못하고 Network로 끝나면 미리 받은 기록으로 오프라인 대결을 3부터 센다(TC-5480 M89)`() = flowTest {
        val gate = CompletableDeferred<Unit>()
        battles.recordStartGate = gate
        battles.recordStart = null
        battles.recordStartFailure = AppError.Network
        val h = open()
        h.vm.onModeSelected(WatchMode.SELF)
        h.vm.onDistanceSelected(Distance(3_000))
        runCurrent()
        assertEquals(WatchFlow.Countdown(WatchMode.SELF, Distance(3_000), 3, isConnecting = true), h.flow)

        gate.complete(Unit)
        runCurrent()

        assertEquals(WatchFlow.Countdown(WatchMode.SELF, Distance(3_000), 3), h.flow)
        passCountdown()
        val running = h.flow as WatchFlow.Running
        assertEquals(WatchMode.SELF, running.mode)
        assertFalse(running.isPausable)
        runAndEnd(h)
        // 오프라인 대결은 서버에 그만두기를 보내지 않고 기록을 대결 ID 없는 자유런으로 남긴다(D-377).
        assertTrue(battles.forfeits.isEmpty())
        assertNull(pendingRuns.saved.single().battleId)
    }

    /** TC-5482(실패 갈래 2, M89): Network가 아닌 실패(서버 500·408)는 카운트다운을 멈추고 거리 선택으로 돌아가 기본 실패 안내를 보인다. */
    @Test
    fun `연결 중 서버 오류로 실패하면 거리 선택으로 돌아가 시작 실패 안내를 보이고 측정하지 않는다(TC-5482 M89)`() = flowTest {
        val h = open()
        val shown = listOf(500, 408).associateWith { status ->
            battles.recordStartFailure = AppError.Server(status)
            h.vm.onModeSelected(WatchMode.SELF)
            h.vm.onDistanceSelected(Distance(3_000))
            runCurrent()
            passCountdown()
            val picker = h.flow as WatchFlow.SelfDistance
            h.vm.back()
            picker.notice
        }

        assertEquals(mapOf(500 to WatchFlow.Notice.START_FAILED, 408 to WatchFlow.Notice.START_FAILED), shown)
        assertFalse(h.tracker.isMeasuring)
        assertEquals(BattleRunState.Idle, h.coordinator.state.value)
    }

    /**
     * TC-5483(경합): 워치 카운트다운에는 "다시 고르기"가 없고 뒤로 가기도 받지 않는다(상태도 2.4.25). 연결 중에 뒤로 가도 화면은 그대로이고
     * 늦게 온 서버 답으로 3부터 센다. 05-20 3.4의 "다시 고르기는 두 하위 상태 모두에서 기존 전이"는 워치에 그 조작이 없어 확인할 수 없다.
     */
    @Test
    fun `연결 중에 뒤로 가도 카운트다운에 머물고 늦게 온 서버 답으로 3부터 센다(TC-5483)`() = flowTest {
        val gate = CompletableDeferred<Unit>()
        battles.recordStartGate = gate
        battles.recordStart = recordStart(CardKind.SELF_RUN, meters = 3_000)
        val h = open()
        h.vm.onModeSelected(WatchMode.SELF)
        h.vm.onDistanceSelected(Distance(3_000))
        runCurrent()

        h.vm.back()
        assertEquals(WatchFlow.Countdown(WatchMode.SELF, Distance(3_000), 3, isConnecting = true), h.flow)
        gate.complete(Unit)
        runCurrent()

        assertEquals(WatchFlow.Countdown(WatchMode.SELF, Distance(3_000), 3), h.flow)
    }

    /** TC-5478(셀프·셀럽 동치 분할): 셀럽런도 서버가 대결을 열 때까지 연결 중이고 답이 오면 3부터 센다(TC-5477과 같다). */
    @Test
    fun `연결된 셀럽런은 서버가 대결을 열 때까지 연결 중을 보이고 답이 오면 3부터 센다(TC-5478)`() = flowTest {
        val gate = CompletableDeferred<Unit>()
        battles.recordStartGate = gate
        battles.recordStart = recordStart(CardKind.CELEB_RUN, meters = 5_000)
        val h = open()
        h.vm.onCelebritySelected(openCelebList(h).celebrities.single())
        runCurrent()
        assertEquals(WatchFlow.Countdown(WatchMode.CELEB, Distance(5_000), 3, isConnecting = true), h.flow)

        gate.complete(Unit)
        runCurrent()

        assertEquals(WatchFlow.Countdown(WatchMode.CELEB, Distance(5_000), 3), h.flow)
        passCountdown()
        assertEquals(WatchMode.CELEB, (h.flow as WatchFlow.Running).mode)
    }

    /** 오프라인 기록 대결(셀프 1km)을 조정자에 바로 연다. 화면 흐름의 회(round) 없이 조정자 단계만 바꿀 때 쓴다. */
    private fun TestScope.openOfflineBattle(h: Harness) {
        h.coordinator.onOfflineRecordBattle(
            OfflineRecordBattle(
                RecordBattleKind.SELF,
                Distance(1_000),
                celebrityId = null,
                track = OpponentTrack.uniform(Distance(1_000), Duration.ofSeconds(300))
            )
        )
        runCurrent()
    }

    /** 서버 대결로 셀럽런 5km를 시작해 측정 화면까지 간다(상대 기록 3m/s). */
    private fun TestScope.startOnlineCeleb(h: Harness) {
        battles.recordStart = recordStart(CardKind.CELEB_RUN, meters = 5_000)
        h.vm.onCelebritySelected(openCelebList(h).celebrities.single())
        runCurrent()
        passCountdown()
        assertEquals(WatchMode.CELEB, (h.flow as WatchFlow.Running).mode)
    }

    /** TC-5521(기초 경로 P1 1→X): 화면 흐름의 회가 없으면(앱 첫 화면) 조정자의 Countdown이 와도 화면 흐름이 바뀌지 않는다. */
    @Test
    fun `화면 흐름에 회가 없으면 조정자 카운트다운이 와도 메인 그대로다(TC-5521 P1)`() = flowTest {
        val h = open()
        val before = h.flow
        assertIs<WatchFlow.Main>(before)

        openOfflineBattle(h)

        assertIs<BattleRunState.Countdown>(h.coordinator.state.value)
        assertEquals(before, h.flow)
        h.coordinator.cancel()
        runCurrent()
        assertEquals(before, h.flow)
    }

    /** TC-5522(기초 경로 P2 1→2a→X): 자유런 회에는 조정자의 Finished를 무시하고 자유런 결과는 측정 끝에서 정한다. */
    @Test
    fun `자유런 중 조정자가 Finished로 끝나도 무시하고 자유런 결과는 측정 끝에서 정한다(TC-5522 P2)`() = flowTest {
        val h = open()
        h.vm.onModeSelected(WatchMode.FREE)
        h.vm.onStartPressed()
        passCountdown()
        val running = h.flow as WatchFlow.Running

        openOfflineBattle(h)
        h.coordinator.forfeit()
        runCurrent()

        assertIs<BattleRunState.Finished>(h.coordinator.state.value)
        assertEquals(running, h.flow)
        assertTrue(h.tracker.isMeasuring)
        runNorth(h.tracker, meters = 200)
        h.vm.onEndLongPressed()
        runCurrent()
        assertEquals(WatchResultHeadline.Finish, h.headline)
    }

    /** TC-5526(기초 경로 P6 …3→6→X): 셀럽런 측정 중 Running 단계의 격차(앞서면 양수)를 측정 화면에 옮기고, 방향은 격차 부호로 앞섬이다. */
    @Test
    fun `셀럽런 측정 중 조정자 격차를 측정 화면에 옮기고 앞서면 앞섬 색이다(TC-5526 P6)`() = flowTest {
        val h = open()
        startOnlineCeleb(h)

        runNorth(h.tracker, meters = 300)

        val gap = (h.coordinator.state.value as BattleRunState.Running).gapMeters
        val running = h.flow as WatchFlow.Running
        assertEquals(gap, running.gapMeters)
        assertTrue(checkNotNull(gap) > 0)
        assertEquals(WatchColors.Lime, leadBannerColor(running))
    }

    /** TC-5527(기초 경로 P7 …3→7→X): 종료 카운트다운(Closing)에는 마지막 격차로 측정 화면을 유지한다. */
    @Test
    fun `종료 카운트다운이 오면 마지막 격차로 측정 화면을 유지한다(TC-5527 P7)`() = flowTest {
        val h = open()
        startOnlineCeleb(h)
        runNorth(h.tracker, meters = 300)
        val lastGap = (h.flow as WatchFlow.Running).gapMeters

        val endsAt = SchedulerClock(testScheduler).instant().plusSeconds(120)
        h.coordinator.onMessage(CountdownStarted(7, "셀럽1", endsAt))
        runCurrent()
        assertIs<BattleRunState.Closing>(h.coordinator.state.value)
        runNorth(h.tracker, meters = 100)

        val running = h.flow as WatchFlow.Running
        assertEquals(WatchMode.CELEB, running.mode)
        assertEquals(lastGap, running.gapMeters)
    }

    /**
     * TC-5530(기초 경로 P10 …3→10→X): 셀럽런 회가 열려 있을 때 조정자가 Idle로 돌아가도 화면 흐름은 그대로다. 측정 중에는 조정자가
     * Idle로 가는 길이 없어(Idle은 Finished·Discarded 확인 뒤에만) 앞 회의 Discarded를 확인하지 않은 채 연결 중인 셀럽런 회에서 Idle을 낸다.
     */
    @Test
    fun `셀럽런 회가 열려 있을 때 조정자가 Idle로 가도 화면 흐름은 그대로다(TC-5530 P10)`() = flowTest {
        val gate = CompletableDeferred<Unit>()
        battles.recordStartGate = gate
        battles.recordStart = recordStart(CardKind.CELEB_RUN, meters = 5_000)
        val h = open()
        openOfflineBattle(h)
        h.coordinator.cancel()
        runCurrent()
        assertIs<BattleRunState.Discarded>(h.coordinator.state.value)
        h.vm.onCelebritySelected(openCelebList(h).celebrities.single())
        runCurrent()
        val connecting = h.flow

        h.coordinator.acknowledgeFinished()
        runCurrent()

        assertEquals(BattleRunState.Idle, h.coordinator.state.value)
        assertEquals(connecting, h.flow)
        gate.complete(Unit)
        runCurrent()
        passCountdown()
        assertEquals(WatchMode.CELEB, (h.flow as WatchFlow.Running).mode)
    }

    /** TC-5471(경곗값 0·1m): 가장 짧은 양수 거리 1m로 끝내면 기록 1건을 저장하고 결과 화면이다(목표 전이라 STOP). 0m는 TC-5469. */
    @Test
    fun `자유런을 1m 달리고 길게 눌러 끝내면 기록을 저장하고 목표 전이라 STOP 결과다(TC-5471)`() = flowTest {
        val h = open()
        h.vm.onModeSelected(WatchMode.FREE)
        h.vm.onDistanceSelected(Distance(3_000))
        h.vm.onStartPressed()
        passCountdown()
        val clock = SchedulerClock(testScheduler)
        h.tracker.onLocationSample(LocationSample(clock.instant(), GeoPoint(START_LAT, START_LNG), 5.0))
        advanceTimeBy(STEP_MILLIS)
        h.tracker.onLocationSample(
            LocationSample(clock.instant(), GeoPoint(START_LAT + ONE_METER_DEGREES, START_LNG), 5.0)
        )
        h.tracker.tick(clock.instant())
        runCurrent()

        h.vm.onEndLongPressed()
        runCurrent()

        assertEquals(1L, pendingRuns.saved.single().distance.meters)
        val result = h.flow as WatchFlow.Result
        assertEquals(WatchResultHeadline.Stop, result.headline)
        assertEquals(1L, result.summary.distance.meters)
    }

    /** TC-5472(종료 화면 2종): 이동 없이 일시정지 화면에서 길게 눌러 끝내도 TC-5469와 같다(저장 0, 메인 안내). */
    @Test
    fun `이동 없이 일시정지 화면에서 길게 눌러 끝내면 저장하지 않았다는 안내와 메인으로 간다(TC-5472)`() = flowTest {
        val h = open()
        h.vm.onModeSelected(WatchMode.FREE)
        h.vm.onStartPressed()
        passCountdown()
        h.vm.onPause()
        runCurrent()
        assertIs<WatchFlow.Paused>(h.flow)

        h.vm.onEndLongPressed()
        runCurrent()

        assertEquals(WatchFlow.Notice.NOT_SAVED, (h.flow as WatchFlow.Main).notice)
        assertTrue(pendingRuns.saved.isEmpty())
    }

    /**
     * TC-5473(W46, D-472): 서버 셀프런 출발 직후 이동 없이 길게 눌러 끝내면 그만두기를 서버에 보내고, 거리 0이라 기록은 만들지 않으며,
     * 메인 "기록할 거리가 없어요" 대신 결과 화면(FR-249)으로 간다.
     */
    @Test
    fun `서버 셀프런 출발 직후 거리 0으로 끝내면 그만두기를 보내고 기록 없이 결과 화면이다(TC-5473 W46)`() = flowTest {
        battles.results[7] = verdict("LOSE", mine = null, theirs = 900)
        val h = open()
        startOnlineSelf(h)

        h.vm.onEndLongPressed()
        runCurrent()

        assertEquals(listOf(7L), battles.forfeits)
        assertTrue(pendingRuns.saved.isEmpty())
        assertEquals(WatchResultHeadline.Stop, h.headline)
    }

    /** TC-5473(W46, D-472): 배틀런도 출발 직후 거리 0으로 끝내면 포기를 보내고 기록 없이 결과 화면이다. */
    @Test
    fun `배틀런 출발 직후 거리 0으로 끝내면 포기를 보내고 기록 없이 결과 화면이다(TC-5473 W46)`() = flowTest {
        battles.results[41] = verdict("LOSE", mine = null, theirs = 300).copy(battleId = 41)
        val h = open()
        requestMatch(h, meters = 1_000)
        live.server.tryEmit(MatchFound(41, "민준", "SILVER", Distance(1_000), null, null, 3))
        runCurrent()
        val startAt = SchedulerClock(testScheduler).instant().plusSeconds(5)
        live.server.tryEmit(BattleStart(41, startAt, opponentTrack = null))
        runCurrent()
        advanceTimeBy(5_000 + WatchFlowViewModel.GO_MILLIS + 300)
        runCurrent()
        assertIs<WatchFlow.Running>(h.flow)

        h.vm.onEndLongPressed()
        runCurrent()

        assertEquals(listOf(41L), battles.forfeits)
        assertTrue(pendingRuns.saved.isEmpty())
        assertEquals(WatchResultHeadline.Stop, h.headline)
    }

    private fun sessionRequests(): Int = dataLayer.sentMessages.count { it.second == WearPaths.SESSION_REQUEST }

    /** 폰 연결이 끊겼다가 다시 닿는다(PhoneLink가 5초마다 노드를 다시 본다). */
    private fun TestScope.reconnectPhone() {
        dataLayer.nodes = emptyList()
        advanceTimeBy(CONNECTION_POLL_MILLIS)
        runCurrent()
        dataLayer.nodes = listOf("phone")
        advanceTimeBy(CONNECTION_POLL_MILLIS)
        runCurrent()
    }

    private companion object {
        /** PhoneLink가 폰 노드를 다시 보는 주기(5초)보다 조금 길게. */
        const val CONNECTION_POLL_MILLIS = 5_100L

        const val MEMBER = 7L
        const val PREFS = "watch-flow-test"
        const val COUNTDOWN_MILLIS = 3_000L + WatchFlowViewModel.GO_MILLIS + 100
        const val START_LAT = 37.5
        const val START_LNG = 127.0
        const val STEP_METERS = 100
        const val STEP_MILLIS = 30_000L

        /** 위도 0.0009도 ≈ 100m. */
        const val STEP_DEGREES = 0.0009

        /** 위도 0.000009도 ≈ 1m(가장 짧은 양수 거리, TC-5471). */
        const val ONE_METER_DEGREES = 0.000009
    }

    @Test
    fun `셀럽 구간 기록 받기가 CELEBRITY_REMOVED가 아닌 거부면 잠시 후 다시 안내다(WR14-1)`() = flowTest {
        battles.celebrityTracks[1] = Outcome.Failure(AppError.Rejected("RATE_LIMITED"))
        val h = open()
        val celebrity = openCelebList(h).celebrities.single()

        h.vm.onCelebritySelected(celebrity)
        runCurrent()
        assertEquals(WatchFlow.Notice.RETRY_LATER, (h.flow as WatchFlow.CelebList).notice)

        battles.celebrityTracks[1] = Outcome.Failure(AppError.Rejected("BAD_REQUEST"))
        h.vm.onCelebritySelected(celebrity)
        runCurrent()
        assertEquals(WatchFlow.Notice.RETRY_LATER, (h.flow as WatchFlow.CelebList).notice)
    }
}
