package com.a207.runkk.app.ui.run

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.core.model.SignalState
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
import com.a207.runkk.app.domain.record.RunSummary
import com.a207.runkk.app.domain.record.TodayPlan
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceOutput
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LateHeartRates
import com.a207.runkk.app.domain.run.LocationPermission
import com.a207.runkk.app.domain.run.MeasurementHost
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.ProximityAlert
import com.a207.runkk.app.domain.run.RoutePoint
import com.a207.runkk.app.domain.run.UploadScheduler
import com.a207.runkk.app.domain.run.WatchDisplay
import com.a207.runkk.app.domain.run.WatchGateway
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** 움직일 수 있는 시계(RunTracker.startRun·finish 시각). */
class MutableClock(var now: Instant = Instant.parse("2026-10-20T10:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now
}

class FakePendingRunRepository : PendingRunRepository {
    val runs = MutableStateFlow<List<CompletedRun>>(emptyList())
    var pendingFailure: AppError? = null

    override suspend fun save(run: CompletedRun): Outcome<Unit> {
        runs.value = runs.value + run
        return Outcome.Success(Unit)
    }

    override suspend fun pending(): Outcome<List<CompletedRun>> =
        pendingFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(runs.value)

    override fun observePending(): Flow<List<CompletedRun>> = runs

    override suspend fun remove(recordId: String) = Outcome.Success(Unit)

    override suspend fun purgeRoutes() = Outcome.Success(Unit)

    override suspend fun purgeHeartRates() = Outcome.Success(Unit)

    override suspend fun needsReminder(now: Instant) = Outcome.Success(false)

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>) = Outcome.Success(Unit)

    override suspend fun pendingLateHeartRates(): Outcome<List<LateHeartRates>> = Outcome.Success(emptyList())

    override suspend fun removeLateHeartRates(sent: LateHeartRates) = Outcome.Success(Unit)

    override suspend fun clear() = Outcome.Success(Unit)
}

class FakeUploadScheduler : UploadScheduler {
    var requests = 0

    override fun requestUpload() {
        requests++
    }
}

class FakeWatchGateway : WatchGateway {
    override val endRequests = MutableSharedFlow<Unit>()
    override val pauseRequests = MutableSharedFlow<WatchGateway.PauseAction>()

    override fun publishProximity(alert: ProximityAlert?) = Unit

    override fun publishLeadChange(kind: Guidance.Kind) = Unit

    val published = mutableListOf<WatchDisplay>()
    override val startRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val heartRates = MutableSharedFlow<List<HeartRateSample>>()

    override fun publish(d: WatchDisplay) {
        published += d
    }

    override fun isConnected() = false
}

class FakeGuidanceOutput : GuidanceOutput {
    val played = mutableListOf<Guidance>()

    override fun play(g: Guidance) {
        played += g
    }
}

class FakeLocationPermission(var granted: Boolean = true) : LocationPermission {
    override fun granted() = granted
}

class FakeMeasurementHost : MeasurementHost {
    var started = 0
    var stopped = 0
    var startFailure: AppError? = null

    override fun startForeground(): Outcome<Unit> {
        started++
        return startFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }

    override fun stop() {
        stopped++
    }
}

class FakeSettingsRepository(var consented: Boolean = true) : SettingsRepository {
    private val snapshot = MutableStateFlow(SettingsSnapshot.SAFE)

    /** 안내 설정 사본(자유런 목표·코치 계획 따르기). 바꾸면 changes()도 방출한다. */
    var guidance: GuidanceSettings
        get() = snapshot.value.guidanceSettings
        set(value) {
            snapshot.value = snapshot.value.copy(guidanceSettings = value)
        }

    override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)

    override fun locationConsented() = consented

    override fun heartRateConsented() = false

    override fun watchLinked() = false

    override fun courseMapShared() = false

    override fun birthYear(): Int? = null

    override fun guidanceSettings() = guidance

    override fun isGuest(): Boolean = false

    override fun changes(): Flow<SettingsSnapshot> = snapshot

    override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
}

class FakeRecordRepository : RecordRepository {
    var todayPlanResult: Outcome<TodayPlan?> = Outcome.Success(TodayPlan(Distance(5_000), Pace(330)))
    var runResult: Outcome<RecordDetail> = Outcome.Failure(AppError.Rejected("NOT_FOUND"))

    override suspend fun dashboard(baseDate: LocalDate): Outcome<Dashboard> = unused()

    override suspend fun runs(ownerId: Long, from: LocalDate, to: LocalDate): Outcome<RecordList> = unused()

    override suspend fun run(recordId: String): Outcome<RecordDetail> = runResult

    override suspend fun growth(period: AggregationPeriod): Outcome<Growth> = unused()

    override suspend fun report(): Outcome<Report> = unused()

    override suspend fun personalRanking(period: AggregationPeriod): Outcome<Ranking> = unused()

    override suspend fun crewRanking(period: AggregationPeriod): Outcome<Ranking> = unused()

    override suspend fun setWeeklyGoal(distance: Distance, runs: Int): Outcome<Unit> = unused()

    override suspend fun putPlan(date: LocalDate, distance: Distance, pace: Pace?): Outcome<Unit> = unused()

    override suspend fun planProgress(from: LocalDate, to: LocalDate): Outcome<PlanProgress> = unused()

    override suspend fun todayPlan(): Outcome<TodayPlan?> = todayPlanResult

    override suspend fun card(recordId: String): Outcome<RunCard> = unused()

    private fun <T> unused(): Outcome<T> = Outcome.Failure(AppError.Rejected("UNUSED"))
}

val start: Instant = Instant.parse("2026-10-20T10:00:00Z")

fun completedRun(id: String = "rec-1", meters: Long = 5_020, heartRates: List<HeartRateSample> = emptyList()) =
    CompletedRun(
        recordId = id,
        mode = RunMode.FREE,
        cardKind = CardKind.FREE_RUN,
        battleId = null,
        crewRunPostId = null,
        startedAt = start,
        elapsed = Duration.ofSeconds(27 * 60 + 48L),
        distance = Distance(meters),
        route = listOf(
            RoutePoint(start, GeoPoint(37.5, 127.0), SignalState.NORMAL),
            RoutePoint(start.plusSeconds(60), GeoPoint(37.51, 127.01), SignalState.NORMAL)
        ),
        unstable = emptyList(),
        heartRates = heartRates,
        savedAt = start.plusSeconds(1_668)
    )

fun recordDetail(id: String = "rec-1") = RecordDetail(
    summary = RunSummary(
        id,
        start,
        Distance(5_020),
        Duration.ofSeconds(1_668),
        Pace(332),
        true,
        RunMode.FREE,
        CardKind.FREE_RUN
    ),
    route = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.51, 127.01)),
    unstableSegments = emptyList(),
    calories = 312,
    heartRateZones = mapOf("Z1" to Duration.ofMinutes(2), "Z3" to Duration.ofMinutes(10)),
    newRecords = listOf("최장 거리 신기록 · 직전 4.80km"),
    crewContribution = null,
    rewardPoints = 50,
    weather = "12°C 맑음"
)
