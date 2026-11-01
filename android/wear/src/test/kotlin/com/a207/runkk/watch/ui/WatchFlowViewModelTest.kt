package com.a207.runkk.watch.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.MySummary
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import com.a207.runkk.app.domain.battle.MatchAcceptance
import com.a207.runkk.app.domain.battle.OpponentTrack
import com.a207.runkk.app.domain.battle.RecordBattleKind
import com.a207.runkk.app.domain.battle.RecordBattleStart
import com.a207.runkk.app.domain.live.BattleStart
import com.a207.runkk.app.domain.live.MatchFailed
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.run.LocationSample
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.app.domain.settings.GuidanceSettings
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
import com.a207.runkk.watch.testing.celebrityTrack
import com.a207.runkk.watch.testing.runTracker
import com.a207.runkk.watch.testing.selfTrack
import com.a207.runkk.wear.contract.SessionRevokedMessage
import com.a207.runkk.wear.contract.WatchDisplayMessage
import com.a207.runkk.wear.contract.WatchSessionMessage
import com.a207.runkk.wear.contract.WearPaths
import java.io.File
import java.time.Duration
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
import org.robolectric.annotation.Config

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
        tracks[RecordBattleKind.CELEBRITY] =
            listOf(celebrityTrack(id = 1, distanceMeters = 5_000, finishSeconds = 1_500))
    }

    private class Harness(
        val vm: WatchFlowViewModel,
        val tracker: RunTracker,
        val coordinator: BattleRunCoordinator,
        val store: WatchSessionStore
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
        val battleFlow =
            WatchBattleFlow(coordinator, battles, cache, MatchWaiter(live, backgroundScope), pendingRuns, clock)
        val vm = WatchFlowViewModel(runs, battleFlow, store, link, network, guidance, accounts)
        runCurrent()
        return Harness(vm, runs, coordinator, store)
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
    fun `셀럽런은 거리 다음 셀럽 목록을 보이고 고르면 카운트다운이다(W09)`() = flowTest {
        online.value = false
        val h = open()
        h.vm.onModeSelected(WatchMode.CELEB)
        val celeb = h.flow as WatchFlow.CelebDistance
        assertEquals(listOf(1, 0, 0, 0), celeb.choices.map { it.count })

        h.vm.onDistanceSelected(Distance(5_000))
        val list = h.flow as WatchFlow.CelebList
        assertEquals("셀럽1", list.celebrities.single().record.displayName)
        h.vm.back()
        assertIs<WatchFlow.CelebDistance>(h.flow)

        h.vm.onDistanceSelected(Distance(5_000))
        h.vm.onCelebritySelected((h.flow as WatchFlow.CelebList).celebrities.single())
        assertEquals(WatchFlow.Countdown(WatchMode.CELEB, Distance(5_000), 3), h.flow)
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

    private companion object {
        const val MEMBER = 7L
        const val PREFS = "watch-flow-test"
        const val COUNTDOWN_MILLIS = 3_000L + WatchFlowViewModel.GO_MILLIS + 100
        const val START_LAT = 37.5
        const val START_LNG = 127.0
        const val STEP_METERS = 100
        const val STEP_MILLIS = 30_000L

        /** 위도 0.0009도 ≈ 100m. */
        const val STEP_DEGREES = 0.0009
    }
}
