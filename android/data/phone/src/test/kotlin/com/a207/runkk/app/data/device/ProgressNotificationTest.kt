package com.a207.runkk.app.data.device

import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.core.model.RunNoticeExtras
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.FreeRunGoalState
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceOutput
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LateHeartRates
import com.a207.runkk.app.domain.run.LocationPermission
import com.a207.runkk.app.domain.run.LocationSample
import com.a207.runkk.app.domain.run.MeasurementHost
import com.a207.runkk.app.domain.run.MeasurementRuntime
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.ProgressNotice
import com.a207.runkk.app.domain.run.ProximityAlert
import com.a207.runkk.app.domain.run.RunArchive
import com.a207.runkk.app.domain.run.RunCoach
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.app.domain.run.RunWatchBridge
import com.a207.runkk.app.domain.run.UploadScheduler
import com.a207.runkk.app.domain.run.WatchDisplay
import com.a207.runkk.app.domain.run.WatchGateway
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 진행 알림 문구·표현 선택·알림 버튼(D-258~D-260): 시험 문서 12.3.1 TC-9605~TC-9608, TC-9611~TC-9616, 12.3.6 TC-9755~TC-9757,
 * TC-9771, 통합 T-36 TC-9841~TC-9846. 서비스 본체 대신 서비스가 쓰는 [MeasurementService.routeAction]·
 * [MeasurementService.followNotices]를 진짜 RunTracker와 함께 돌린다(워치·보관·권한은 가짜).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProgressNotificationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun free(elapsed: Long, meters: Long, goal: FreeRunGoalState, isPaused: Boolean = false) =
        ProgressNotice.Free(Duration.ofSeconds(elapsed), Distance(meters), goal, isPaused)

    private fun battle(gap: Int?) =
        ProgressNotice.Battle("바람처럼", Duration.ofSeconds(940), Distance(2_140), gap, Distance(3_000))

    private fun Notification.title(): String? = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun Notification.body(): String? = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    private fun Notification.actionTitles(): List<String> = actions.orEmpty().map { it.title.toString() }

    // ---------- 문구 (TC-9605~TC-9608) ----------

    @Test
    fun `TC-9605 자유런 알림은 경과 제목, 거리, 목표까지 남은 거리, 일시정지 버튼이다`() {
        val n = OngoingProgressStyle(context).build(free(712, 2_140, FreeRunGoalState.Remaining(Distance(2_860))))
        assertEquals("자유런 · 11:52 경과", n.title())
        assertEquals("2.14 km   목표까지 2.86km", n.body())
        assertEquals(listOf("일시정지"), n.actionTitles())
        val text = ProgressNoticeText.of(context, free(712, 2_140, FreeRunGoalState.Reached))
        assertEquals("목표 달성", text.detail)
        assertNull(ProgressNoticeText.of(context, free(712, 2_140, FreeRunGoalState.None)).detail, "목표 자유는 줄 없음")
    }

    @Test
    fun `TC-9606 대결 알림은 상대 이름 제목과 앞섬·뒤처짐 줄이고 연결 확인 중이면 줄이 없다`() {
        assertEquals("vs 바람처럼 · 15:40 경과", ProgressNoticeText.of(context, battle(42)).title)
        assertEquals("앞서는 중 +42 m", ProgressNoticeText.of(context, battle(42)).detail)
        assertEquals("뒤처지는 중 -118 m", ProgressNoticeText.of(context, battle(-118)).detail)
        assertNull(ProgressNoticeText.of(context, battle(null)).detail)
        assertEquals("앞서는 중 +0 m", ProgressNoticeText.of(context, battle(0)).detail)
    }

    @Test
    fun `TC-9607 대결 알림에는 동작 버튼이 없다`() {
        for (gap in listOf(42, -118, null)) {
            assertTrue(OngoingProgressStyle(context).build(battle(gap)).actionTitles().isEmpty())
        }
    }

    @Test
    fun `TC-9608 자유런 버튼은 측정 중 일시정지, 정지 중 재개다`() {
        val running = OngoingProgressStyle(context).build(free(10, 100, FreeRunGoalState.None))
        val paused = OngoingProgressStyle(context).build(free(10, 100, FreeRunGoalState.None, isPaused = true))
        assertEquals(listOf("일시정지"), running.actionTitles())
        assertEquals(listOf("재개"), paused.actionTitles())
    }

    @Test
    fun `TC-9609 진행 막대 끝은 목표 거리·대결 거리이고 목표 자유는 막대가 없다`() {
        val bar = OngoingProgressStyle(context).build(free(10, 2_500, FreeRunGoalState.Remaining(Distance(2_500))))
        assertEquals(5_000, bar.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertEquals(2_500, bar.extras.getInt(Notification.EXTRA_PROGRESS))
        val none = OngoingProgressStyle(context).build(free(10, 2_500, FreeRunGoalState.None))
        assertEquals(0, none.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        val versus = ProgressNoticeText.of(context, battle(10).copy(distance = Distance(1_000)))
        assertEquals(1_000 to 3_000, versus.progress to versus.progressMax)
    }

    @Test
    fun `S15P31A207-278 대결 알림을 누르면 대결 ID를 실어 앱을 열고 자유런 알림은 싣지 않는다`() {
        val launch = Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
        val versus = progressOpenIntent(launch, ProgressNoticeText.of(context, battle(42).copy(battleId = 9)))
        assertEquals(9L, versus.getLongExtra(RunNoticeExtras.BATTLE_ID, 0))
        val unknown = progressOpenIntent(launch, ProgressNoticeText.of(context, battle(42)))
        assertFalse(unknown.hasExtra(RunNoticeExtras.BATTLE_ID), "대결 ID가 없으면 앱만 연다")
        val free = progressOpenIntent(launch, ProgressNoticeText.of(context, free(10, 100, FreeRunGoalState.None)))
        assertFalse(free.hasExtra(RunNoticeExtras.BATTLE_ID))
        assertFalse(launch.hasExtra(RunNoticeExtras.BATTLE_ID), "실행 인텐트 원본은 바꾸지 않는다")
    }

    // ---------- 표현 선택 (TC-9611~TC-9613, TC-9755~TC-9757, TC-9771) ----------

    @Test
    fun `TC-9611 TC-9612 TC-9771 API 36 이상만 Live Updates이고 범위 밖 값도 예외가 없다`() {
        val expected = mapOf(34 to false, 35 to false, 36 to true, 37 to true, 0 to false, 99 to true)
        for ((sdk, isLive) in expected) {
            assertEquals(isLive, ProgressNotificationStyle.isLiveUpdate(sdk) { true }, "SDK $sdk")
        }
        assertTrue(ProgressNotificationStyle.choose(context, 36) { true } is LiveUpdateStyle)
        assertTrue(ProgressNotificationStyle.choose(context, 35) { true } is OngoingProgressStyle)
    }

    @Test
    fun `TC-9613 TC-9756 API 36이어도 승격을 못 올리면 일반 진행 알림으로 물러난다`() {
        assertFalse(ProgressNotificationStyle.isLiveUpdate(36) { false })
        assertTrue(ProgressNotificationStyle.choose(context, 36) { false } is OngoingProgressStyle)
    }

    @Test
    fun `TC-9755 TC-9757 API 35면 승격 가능 여부를 묻지 않고, 36에 가능하면 Live Updates다`() {
        var asked = 0
        assertFalse(ProgressNotificationStyle.isLiveUpdate(35) { asked++ > -1 })
        assertEquals(0, asked)
        assertTrue(ProgressNotificationStyle.isLiveUpdate(36) { asked++ > -1 })
        assertEquals(1, asked)
    }

    // Robolectric의 SDK 36 판은 Java 21이 있어야 해서(로컬·CI 시험 JVM 17) 기본 SDK 35에서 compat 값으로 본다.
    @Test
    fun `TC-9614 Live Updates는 승격 요청, 칩 글자, 공개 표시, 한 번만 알림이다`() {
        val freeN = LiveUpdateStyle(context).build(free(712, 2_140, FreeRunGoalState.Remaining(Distance(2_860))))
        assertEquals("2.14 km", NotificationCompat.getShortCriticalText(freeN)?.toString())
        assertTrue(NotificationCompat.isRequestPromotedOngoing(freeN))
        assertEquals(NotificationCompat.VISIBILITY_PUBLIC, freeN.visibility)
        assertTrue(freeN.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(MeasurementService.CHANNEL_ID, freeN.channelId)
        val battleN = LiveUpdateStyle(context).build(battle(42))
        assertEquals("+42 m", NotificationCompat.getShortCriticalText(battleN)?.toString())
    }

    @Test
    fun `TC-9843 두 표현은 같은 제목·거리·남은 거리 문구다`() {
        val notice = free(712, 2_140, FreeRunGoalState.Remaining(Distance(2_860)))
        val ongoing = OngoingProgressStyle(context).build(notice)
        val live = LiveUpdateStyle(context).build(notice)
        assertEquals("자유런 · 11:52 경과", ongoing.title())
        assertEquals("2.14 km   목표까지 2.86km", ongoing.body())
        assertEquals(ongoing.title(), live.title())
        assertEquals(ongoing.body(), live.body())
        assertEquals(ongoing.actionTitles(), live.actionTitles())
    }

    @Test
    fun `TC-9622 정지 자동 종료·잠금 해제·고정 측정 알림 문자열이 없다`() {
        val names = com.a207.runkk.app.data.phone.R.string::class.java.fields.map { it.name }.toSet()
        val removed = setOf(
            "guidance_pause_auto_end_soon",
            "guidance_pause_auto_ended",
            "guidance_unlock_hint",
            "watch_notice_auto_end_soon",
            "watch_notice_auto_ended",
            "measurement_notification_title",
            "measurement_notification_text"
        )
        assertEquals(emptySet(), names intersect removed)
    }

    // ---------- 서비스 action·알림 갱신 (TC-9615, TC-9616, T-36) ----------

    @Test
    fun `TC-9616 알림 action은 RunTracker 알림 연산만 부르고 포그라운드 진입으로 가지 않는다`() {
        val calls = mutableListOf<String>()
        assertTrue(
            MeasurementService.routeAction(MeasurementService.ACTION_PAUSE, { calls += "pause" }, {
                calls +=
                    "resume"
            })
        )
        assertTrue(
            MeasurementService.routeAction(MeasurementService.ACTION_RESUME, { calls += "pause" }, {
                calls +=
                    "resume"
            })
        )
        assertFalse(MeasurementService.routeAction(null, { calls += "pause" }, { calls += "resume" }), "시작 요청")
        assertFalse(MeasurementService.routeAction("other", { calls += "pause" }, { calls += "resume" }))
        assertEquals(listOf("pause", "resume"), calls)
    }

    @Test
    fun `TC-9615 TC-9845 진행 값이 바뀔 때마다 같은 알림으로 다시 그리고 1초마다 한 번이다`() = runTest {
        val world = World(this)
        val posted = world.follow()
        world.tracker.startRun(world.free)
        runCurrent()
        posted.clear()
        for (s in 1..10L) {
            world.clock.now = T0.plusSeconds(s)
            world.tracker.tick(world.clock.now)
            runCurrent()
        }
        assertEquals(10, posted.size, "1초 tick 10번에 10번 갱신")
        assertEquals((1..10).map { "자유런 · 00:%02d 경과".format(it) }, posted.map { it.title() })
    }

    @Test
    fun `TC-9841 TC-9842 알림 일시정지·재개는 RunSession 상태와 버튼을 바꾸고 워치에 정지를 보낸다`() = runTest {
        val world = World(this)
        val posted = world.follow()
        world.tracker.startRun(world.free)
        world.tracker.onLocationSample(LocationSample(T0.plusSeconds(1), north(0.0), 5.0))
        world.tracker.onLocationSample(LocationSample(T0.plusSeconds(200), north(900.0), 5.0))
        world.clock.now = T0.plusSeconds(200)
        world.tracker.tick(world.clock.now)
        runCurrent()
        assertEquals(listOf("일시정지"), posted.last().actionTitles())

        world.clock.now = T0.plusSeconds(201)
        MeasurementService.routeAction(
            MeasurementService.ACTION_PAUSE,
            world.tracker::onNotificationPauseTapped,
            world.tracker::onNotificationResumeTapped
        )
        runCurrent()
        assertTrue(world.tracker.progress.value.isPaused)
        assertEquals(listOf("재개"), posted.last().actionTitles())
        assertEquals(1, world.watch.published.count { it.isPaused }, "워치 정지 표시 1회")

        world.clock.now = T0.plusSeconds(261)
        MeasurementService.routeAction(
            MeasurementService.ACTION_RESUME,
            world.tracker::onNotificationPauseTapped,
            world.tracker::onNotificationResumeTapped
        )
        runCurrent()
        assertFalse(world.tracker.progress.value.isPaused)
        assertEquals(listOf("일시정지"), posted.last().actionTitles())
        world.tracker.onLocationSample(LocationSample(T0.plusSeconds(262), north(905.0), 5.0))
        world.tracker.onLocationSample(LocationSample(T0.plusSeconds(300), north(1_100.0), 5.0))
        world.clock.now = T0.plusSeconds(300)
        world.tracker.tick(world.clock.now)
        runCurrent()
        assertEquals(Distance(1_095), world.tracker.progress.value.distance, "재개 뒤 거리 누적")
        assertEquals(Duration.ofSeconds(240), world.tracker.progress.value.elapsed, "정지 60초 제외")
    }

    @Test
    fun `TC-9844 대결 알림에는 버튼이 없고 일시정지 action을 받아도 RUNNING 그대로다`() = runTest {
        val world = World(this)
        val posted = world.follow()
        world.tracker.startRun(
            RunStartSpec(
                mode = RunMode.BATTLE,
                cardKind = CardKind.BATTLE_RUN,
                battleId = 3,
                todayPlan = PlanSnapshot(Distance(3_000), null),
                opponentName = "바람처럼"
            )
        )
        world.tracker.tick(T0.plusSeconds(1))
        runCurrent()
        assertTrue(posted.last().actionTitles().isEmpty())
        MeasurementService.routeAction(
            MeasurementService.ACTION_PAUSE,
            world.tracker::onNotificationPauseTapped,
            world.tracker::onNotificationResumeTapped
        )
        runCurrent()
        assertFalse(world.tracker.progress.value.isPaused)
        world.tracker.endRun()
    }

    @Test
    fun `TC-9846 종료·자유런 취소면 알림을 내리고 서비스를 멈춘다`() = runTest {
        for (end in listOf<(RunTracker) -> Unit>({ it.onFinishLongPressed() }, { it.discardRun() })) {
            val world = World(this)
            world.follow()
            world.tracker.startRun(world.free)
            world.tracker.tick(T0.plusSeconds(1))
            runCurrent()
            end(world.tracker)
            runCurrent()
            assertEquals(1, world.removed, "알림 내림 1회")
            assertEquals(1, world.host.stopped, "서비스 중지")
        }
    }

    /** 진짜 RunTracker와 가짜 포트들. */
    private class World(private val scope: TestScope) {
        val clock = MutableClock(T0)
        val watch = FakeWatch()
        val host = FakeHost()
        var removed = 0
        val free =
            RunStartSpec(
                mode = RunMode.FREE,
                cardKind = CardKind.FREE_RUN,
                todayPlan = PlanSnapshot(Distance(5_000), null)
            )
        val tracker = RunTracker(
            RunArchive(FakePending(), NoUpload, scope.backgroundScope),
            RunWatchBridge(watch, scope.backgroundScope),
            RunCoach(NoGuidance, FakeSettings),
            MeasurementRuntime(Granted, host, FakeSettings, clock)
        )

        fun follow(): MutableList<Notification> {
            val posted = mutableListOf<Notification>()
            val context: Context = ApplicationProvider.getApplicationContext()
            scope.backgroundScope.launch {
                MeasurementService.followNotices(
                    tracker.notice,
                    OngoingProgressStyle(context),
                    post = { posted += it },
                    remove = { removed++ }
                )
            }
            scope.runCurrent()
            return posted
        }
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = now
    }

    private class FakeWatch : WatchGateway {
        val published = mutableListOf<WatchDisplay>()

        override fun publish(d: WatchDisplay) {
            published += d
        }

        override val startRequests: Flow<Unit> = MutableSharedFlow()
        override val endRequests: Flow<Unit> = MutableSharedFlow()
        override val pauseRequests: Flow<WatchGateway.PauseAction> = MutableSharedFlow()
        override val heartRates: Flow<List<HeartRateSample>> = emptyFlow()

        override fun isConnected(): Boolean = false

        override fun publishProximity(alert: ProximityAlert?) = Unit

        override fun publishLeadChange(kind: Guidance.Kind) = Unit
    }

    private class FakeHost : MeasurementHost {
        var stopped = 0

        override fun startForeground(): Outcome<Unit> = Outcome.Success(Unit)

        override fun stop() {
            stopped++
        }
    }

    private class FakePending : PendingRunRepository {
        override suspend fun save(run: CompletedRun): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun pending(): Outcome<List<CompletedRun>> = Outcome.Success(emptyList())

        override fun observePending(): Flow<List<CompletedRun>> = MutableStateFlow(emptyList())

        override suspend fun remove(recordId: String): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun purgeRoutes(): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun purgeHeartRates(): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun needsReminder(now: Instant): Outcome<Boolean> = Outcome.Success(false)

        override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit> =
            Outcome.Success(Unit)

        override suspend fun pendingLateHeartRates(): Outcome<List<LateHeartRates>> = Outcome.Success(emptyList())

        override suspend fun removeLateHeartRates(sent: LateHeartRates): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
    }

    private object NoUpload : UploadScheduler {
        override fun requestUpload() = Unit
    }

    private object NoGuidance : GuidanceOutput {
        override fun play(g: Guidance) = Unit
    }

    private object Granted : LocationPermission {
        override fun granted(): Boolean = true
    }

    private object FakeSettings : SettingsRepository {
        private val snapshot = SettingsSnapshot(true, false, false, false, null, GuidanceSettings.DEFAULT)

        override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)

        override fun locationConsented() = true

        override fun heartRateConsented() = false

        override fun watchLinked() = false

        override fun courseMapShared() = false

        override fun birthYear(): Int? = null

        override fun guidanceSettings() = GuidanceSettings.DEFAULT

        override fun isGuest(): Boolean = false

        override fun changes(): Flow<SettingsSnapshot> = MutableStateFlow(snapshot)

        override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
    }

    private companion object {
        val T0: Instant = Instant.parse("2026-10-27T00:00:00Z")
        const val METERS_PER_DEGREE = 6_371_000.0 * Math.PI / 180.0

        fun north(meters: Double): GeoPoint = GeoPoint(37.0 + meters / METERS_PER_DEGREE, 127.0)
    }
}
