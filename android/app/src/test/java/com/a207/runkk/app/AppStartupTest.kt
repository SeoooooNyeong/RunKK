package com.a207.runkk.app

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.notification.LoginState
import com.a207.runkk.app.domain.notification.PushTokenRemote
import com.a207.runkk.app.domain.notification.PushTokenSource
import com.a207.runkk.app.domain.notification.PushTokenSync
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.Dashboard
import com.a207.runkk.app.domain.record.Growth
import com.a207.runkk.app.domain.record.PlanProgress
import com.a207.runkk.app.domain.record.Ranking
import com.a207.runkk.app.domain.record.RecordDetail
import com.a207.runkk.app.domain.record.RecordList
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.record.Report
import com.a207.runkk.app.domain.record.RunCard
import com.a207.runkk.app.domain.record.TodayPlan
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.FreeRunGoalUseCase
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceOutput
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LateHeartRates
import com.a207.runkk.app.domain.run.LocationPermission
import com.a207.runkk.app.domain.run.MeasurementHost
import com.a207.runkk.app.domain.run.MeasurementRuntime
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.ProximityAlert
import com.a207.runkk.app.domain.run.RunArchive
import com.a207.runkk.app.domain.run.RunCoach
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.app.domain.run.RunWatchBridge
import com.a207.runkk.app.domain.run.TodayPlanCache
import com.a207.runkk.app.domain.run.UploadScheduler
import com.a207.runkk.app.domain.run.WatchDisplay
import com.a207.runkk.app.domain.run.WatchGateway
import com.a207.runkk.app.domain.run.WatchHomeSync
import com.a207.runkk.app.domain.settings.FirstRunGuide
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.GuideCopy
import com.a207.runkk.app.domain.settings.GuideRemote
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import com.a207.runkk.app.domain.sync.DisposalOrder
import com.a207.runkk.app.domain.sync.DisposalRemote
import com.a207.runkk.app.domain.sync.SyncDisposalsUseCase
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * 앱 시작 작업 순서와 로그인 전환(13.2, D-43·D-53·D-54): 가짜 포트가 부른 순서를 한 목록에 기록해 확인한다.
 * 워치 시작 화면(FR-042)은 실제 RunTracker·WatchHomeSync에 가짜 워치를 물려 보낸 표시를 확인한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppStartupTest {
    private val events = mutableListOf<String>()
    private val login = MutableStateFlow(true)
    private val settings = RecordingSettings(events)
    private val pushRemote = RecordingPushRemote(events)
    private val records = TodayPlanRecords()
    private val watch = RecordingWatch()
    private val todayPlans = TodayPlanCache()

    private fun TestScope.startup(): AppStartup {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val pending = NoPendingRuns()
        val tracker =
            RunTracker(
                RunArchive(pending, NoUpload(), scope),
                RunWatchBridge(watch, scope),
                RunCoach(SilentGuidance(), settings),
                MeasurementRuntime(
                    NoLocationPermission(),
                    NoMeasurementHost(),
                    settings,
                    Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC)
                )
            )
        val watchHome = WatchHomeSync(tracker, FreeRunGoalUseCase(settings), todayPlans, scope)
        val disposals = SyncDisposalsUseCase(pending, RecordingDisposalRemote(events))
        val loginState =
            object : LoginState {
                override val loggedIn: StateFlow<Boolean> = login
            }
        val push = PushTokenSync(pushRemote, FixedPushToken("fcm-token"), loginState, scope)
        val guide = FirstRunGuide(settings, RecordingGuideRemote(events))
        return AppStartup(disposals, settings, push, loginState, records, todayPlans, watchHome, guide, scope)
    }

    @Test
    fun `시작하면 오늘 계획을 읽어 워치 시작 화면에 싣는다(FR-042)`() = runTest {
        startup().start()
        advanceUntilIdle()

        assertTrue(watch.published.isNotEmpty(), "워치 시작 화면을 보내지 않았다")
        val home = watch.published.last()
        assertEquals(false, home.isRunning)
        assertEquals(PlanSnapshot(Distance(5_000), Pace(330)), home.todayPlan)
    }

    @Test
    fun `로그아웃 상태로 시작하면 오늘 계획을 읽지 않고 시작 화면만 보낸다`() = runTest {
        login.value = false

        startup().start()
        advanceUntilIdle()

        assertEquals(0, records.todayPlanCalls)
        assertTrue(watch.published.isNotEmpty(), "워치 시작 화면을 보내지 않았다")
        assertNull(watch.published.last().todayPlan)
    }

    @Test
    fun `로그아웃하면 이전 회원의 오늘 계획을 워치 시작 화면에서 지운다`() = runTest {
        startup().start()
        advanceUntilIdle()

        login.value = false
        advanceUntilIdle()

        assertNull(todayPlans.plan.value)
        assertNull(watch.published.last().todayPlan)
    }

    @Test
    fun `시작하면 파기 지시, 설정 갱신, 푸시 토큰 등록 순서로 한다`() = runTest {
        startup().start()
        advanceUntilIdle()

        assertEquals(listOf("disposals", "settings.refresh", "push.register"), events)
    }

    @Test
    fun `TC-9715 서버에 알리지 못한 가이드 끝냄은 시작 때 다시 보낸다`() = runTest {
        settings.guide = GuideCopy.SEEN_UNSENT

        startup().start()
        advanceUntilIdle()

        assertEquals(listOf("disposals", "settings.refresh", "push.register", "guide.markSeen"), events)
        assertEquals(GuideCopy.SEEN, settings.guide)
    }

    @Test
    fun `로그아웃 상태로 시작하면 푸시 등록은 건너뛴다`() = runTest {
        login.value = false

        startup().start()
        advanceUntilIdle()

        assertEquals(listOf("disposals", "settings.refresh"), events)
    }

    @Test
    fun `로그인 상태가 참이 되면 세 작업을 다시 한다`() = runTest {
        login.value = false
        startup().start()
        advanceUntilIdle()
        events.clear()

        login.value = true
        advanceUntilIdle()

        assertEquals(listOf("disposals", "settings.refresh", "push.register"), events)
    }

    @Test
    fun `로그인 상태가 거짓이 되면 설정 사본을 지운다`() = runTest {
        startup().start()
        advanceUntilIdle()
        events.clear()

        login.value = false
        advanceUntilIdle()

        assertEquals(listOf("settings.clear"), events)
    }

    @Test
    fun `로그아웃 뒤 다시 로그인하면 지우고 나서 다시 받는다`() = runTest {
        startup().start()
        advanceUntilIdle()
        events.clear()

        login.value = false
        advanceUntilIdle()
        login.value = true
        advanceUntilIdle()

        assertEquals(
            listOf("settings.clear", "disposals", "settings.refresh", "push.register"),
            events
        )
    }

    @Test
    fun `시작 작업이 실패해도 뒤 작업과 로그인 전환 구독은 이어진다`() = runTest {
        settings.refreshResult = Outcome.Failure(AppError.Network)
        pushRemote.result = Outcome.Failure(AppError.Network)
        startup().start()
        advanceUntilIdle()

        login.value = false
        advanceUntilIdle()

        assertEquals(listOf("disposals", "settings.refresh", "push.register", "settings.clear"), events)
    }
}

private class RecordingDisposalRemote(private val events: MutableList<String>) : DisposalRemote {
    override suspend fun pending(): Outcome<List<DisposalOrder>> {
        events += "disposals"
        return Outcome.Success(emptyList())
    }

    override suspend fun confirm(orderId: Long): Outcome<Unit> = Outcome.Success(Unit)
}

private class RecordingPushRemote(private val events: MutableList<String>) : PushTokenRemote {
    var result: Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun register(token: String): Outcome<Unit> {
        events += "push.register"
        return result
    }
}

private class FixedPushToken(private val token: String?) : PushTokenSource {
    override suspend fun currentToken(): String? = token
}

/** 코치 계획 따르기가 켜진 사본(오늘 계획이 워치 시작 화면 목표가 된다). */
private class RecordingSettings(private val events: MutableList<String>) : SettingsRepository {
    var refreshResult: Outcome<Unit> = Outcome.Success(Unit)
    private val snapshot =
        MutableStateFlow(
            SettingsSnapshot.SAFE.copy(guidanceSettings = GuidanceSettings.DEFAULT.copy(isFollowingCoachPlan = true))
        )

    override suspend fun refresh(): Outcome<Unit> {
        events += "settings.refresh"
        return refreshResult
    }

    override fun locationConsented(): Boolean = false

    override fun heartRateConsented(): Boolean = false

    override fun watchLinked(): Boolean = false

    override fun courseMapShared(): Boolean = false

    override fun birthYear(): Int? = null

    override fun guidanceSettings(): GuidanceSettings = snapshot.value.guidanceSettings

    override fun isGuest(): Boolean = snapshot.value.isGuest

    override fun changes(): Flow<SettingsSnapshot> = snapshot

    override suspend fun clear(): Outcome<Unit> {
        events += "settings.clear"
        return Outcome.Success(Unit)
    }

    var guide: GuideCopy = GuideCopy.NONE

    override suspend fun guideCopy(): GuideCopy = guide

    override suspend fun saveGuideCopy(copy: GuideCopy): Outcome<Unit> {
        guide = copy
        return Outcome.Success(Unit)
    }
}

private class RecordingGuideRemote(private val events: MutableList<String>) : GuideRemote {
    override suspend fun seen(): Outcome<Boolean> = Outcome.Success(false)

    override suspend fun markSeen(): Outcome<Unit> {
        events += "guide.markSeen"
        return Outcome.Success(Unit)
    }
}

/** 파기 지시가 없는 시험이라 보관함은 건드리지 않는다. */
private class NoPendingRuns : PendingRunRepository {
    override suspend fun save(run: CompletedRun): Outcome<Unit> = unused()

    override suspend fun pending(): Outcome<List<CompletedRun>> = unused()

    override fun observePending(): Flow<List<CompletedRun>> = emptyFlow()

    override suspend fun remove(recordId: String): Outcome<Unit> = unused()

    override suspend fun purgeRoutes(): Outcome<Unit> = unused()

    override suspend fun purgeHeartRates(): Outcome<Unit> = unused()

    override suspend fun needsReminder(now: Instant): Outcome<Boolean> = unused()

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit> = unused()

    override suspend fun pendingLateHeartRates(): Outcome<List<LateHeartRates>> = unused()

    override suspend fun removeLateHeartRates(sent: LateHeartRates): Outcome<Unit> = unused()

    override suspend fun clear(): Outcome<Unit> = unused()

    private fun <T> unused(): T = throw UnsupportedOperationException("이 시험은 보관함을 쓰지 않는다")
}

/** 오늘 계획 5km·5분 30초. 오늘 계획 말고는 쓰지 않는다. */
private class TodayPlanRecords : RecordRepository {
    var todayPlanCalls = 0

    override suspend fun todayPlan(): Outcome<TodayPlan?> {
        todayPlanCalls++
        return Outcome.Success(TodayPlan(Distance(5_000), Pace(330)))
    }

    override suspend fun dashboard(baseDate: LocalDate): Outcome<Dashboard> = unused()

    override suspend fun runs(ownerId: Long, from: LocalDate, to: LocalDate): Outcome<RecordList> = unused()

    override suspend fun run(recordId: String): Outcome<RecordDetail> = unused()

    override suspend fun growth(period: AggregationPeriod): Outcome<Growth> = unused()

    override suspend fun report(): Outcome<Report> = unused()

    override suspend fun personalRanking(period: AggregationPeriod): Outcome<Ranking> = unused()

    override suspend fun crewRanking(period: AggregationPeriod): Outcome<Ranking> = unused()

    override suspend fun setWeeklyGoal(distance: Distance, runs: Int): Outcome<Unit> = unused()

    override suspend fun putPlan(date: LocalDate, distance: Distance, pace: Pace?): Outcome<Unit> = unused()

    override suspend fun planProgress(from: LocalDate, to: LocalDate): Outcome<PlanProgress> = unused()

    override suspend fun card(recordId: String): Outcome<RunCard> = unused()

    private fun <T> unused(): T = throw UnsupportedOperationException("이 시험은 오늘 계획만 읽는다")
}

private class RecordingWatch : WatchGateway {
    override val pauseRequests: Flow<WatchGateway.PauseAction> = MutableSharedFlow()

    override fun publishProximity(alert: ProximityAlert?) = Unit

    override fun publishLeadChange(kind: Guidance.Kind) = Unit

    val published = mutableListOf<WatchDisplay>()
    override val startRequests: Flow<Unit> = MutableSharedFlow()
    override val endRequests: Flow<Unit> = MutableSharedFlow()
    override val heartRates: Flow<List<HeartRateSample>> = MutableSharedFlow()

    override fun publish(d: WatchDisplay) {
        published += d
    }

    override fun isConnected(): Boolean = true
}

private class NoUpload : UploadScheduler {
    override fun requestUpload() = Unit
}

private class SilentGuidance : GuidanceOutput {
    override fun play(g: Guidance) = Unit
}

private class NoLocationPermission : LocationPermission {
    override fun granted(): Boolean = false
}

private class NoMeasurementHost : MeasurementHost {
    override fun startForeground(): Outcome<Unit> = Outcome.Failure(AppError.Storage)

    override fun stop() = Unit
}
