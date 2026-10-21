package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
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
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

fun summary(id: String, day: Int, meters: Long, valid: Boolean = true, kind: CardKind = CardKind.FREE_RUN) = RunSummary(
    recordId = id,
    startedAt = LocalDate.of(2026, 10, day).atTime(7, 0).atZone(java.time.ZoneId.of("Asia/Seoul")).toInstant(),
    distance = Distance(meters),
    elapsed = Duration.ofSeconds(meters * 332 / 1_000),
    averagePace = Pace(332),
    valid = valid,
    mode = if (kind == CardKind.FREE_RUN) RunMode.FREE else RunMode.BATTLE,
    cardKind = kind
)

fun detail(id: String = "r1") = RecordDetail(
    summary = summary(id, 20, 5_020),
    route = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.51, 127.01)),
    unstableRanges = emptyList(),
    calories = 312,
    heartRateZones = mapOf("Z2" to Duration.ofMinutes(10)),
    newRecords = emptyList(),
    crewContribution = Distance(5_020),
    rewardPoints = 50,
    weather = "12°C 맑음"
)

fun runCard(shared: Boolean = true) = RunCard(
    recordId = "r1",
    cardKind = CardKind.FREE_RUN,
    startedAt = Instant.parse("2026-10-20T10:00:00Z"),
    distance = Distance(5_020),
    elapsed = Duration.ofSeconds(1_668),
    averagePace = Pace(332),
    route = listOf(GeoPoint(37.5, 127.0), GeoPoint(37.51, 127.01)),
    courseMapShared = shared
)

class FakeRecordRepository : RecordRepository {
    var runsResult: Outcome<RecordList> = Outcome.Success(
        RecordList(
            listOf(
                summary("r3", 20, 5_020),
                summary("r2", 14, 10_200, kind = CardKind.BATTLE_RUN),
                summary("r1", 14, 2_000, valid = false)
            )
        )
    )
    var runResult: Outcome<RecordDetail> = Outcome.Success(detail())
    var cardResult: Outcome<RunCard> = Outcome.Success(runCard())
    val runsCalls = mutableListOf<Triple<Long, LocalDate, LocalDate>>()

    override suspend fun dashboard(baseDate: LocalDate): Outcome<Dashboard> = unused()

    override suspend fun runs(ownerId: Long, from: LocalDate, to: LocalDate): Outcome<RecordList> {
        runsCalls += Triple(ownerId, from, to)
        return runsResult
    }

    override suspend fun run(recordId: String): Outcome<RecordDetail> = runResult

    override suspend fun growth(period: AggregationPeriod): Outcome<Growth> = unused()

    override suspend fun report(): Outcome<Report> = unused()

    override suspend fun personalRanking(period: AggregationPeriod): Outcome<Ranking> = unused()

    override suspend fun crewRanking(period: AggregationPeriod): Outcome<Ranking> = unused()

    override suspend fun setWeeklyGoal(distance: Distance?, runs: Int?): Outcome<Unit> = unused()

    override suspend fun putPlan(date: LocalDate, distance: Distance, pace: Pace?): Outcome<Unit> = unused()

    override suspend fun planProgress(from: LocalDate, to: LocalDate): Outcome<PlanProgress> = unused()

    override suspend fun todayPlan(): Outcome<TodayPlan?> = unused()

    override suspend fun card(recordId: String): Outcome<RunCard> = cardResult

    private fun <T> unused(): Outcome<T> = Outcome.Failure(AppError.Rejected("UNUSED"))
}

class FakePendingRunRepository : PendingRunRepository {
    val runs = MutableStateFlow<List<CompletedRun>>(emptyList())

    override suspend fun save(run: CompletedRun) = Outcome.Success(Unit)

    override suspend fun pending(): Outcome<List<CompletedRun>> = Outcome.Success(runs.value)

    override fun observePending(): Flow<List<CompletedRun>> = runs

    override suspend fun remove(recordId: String) = Outcome.Success(Unit)

    override suspend fun purgeRoutes() = Outcome.Success(Unit)

    override suspend fun purgeHeartRates() = Outcome.Success(Unit)

    override suspend fun needsReminder(now: Instant) = Outcome.Success(false)

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>) = Outcome.Success(Unit)

    override suspend fun clear() = Outcome.Success(Unit)
}

class FakeSettingsRepository(var shared: Boolean = true) : SettingsRepository {
    override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)

    override fun locationConsented() = true

    override fun heartRateConsented() = false

    override fun watchLinked() = false

    override fun courseMapShared() = shared

    override fun birthYear(): Int? = null

    override fun guidanceSettings() = GuidanceSettings.DEFAULT

    override fun changes(): Flow<SettingsSnapshot> = flowOf(SettingsSnapshot.SAFE)

    override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
}

fun pendingRun(id: String) = CompletedRun(
    recordId = id,
    mode = RunMode.FREE,
    cardKind = CardKind.FREE_RUN,
    battleId = null,
    crewRunPostId = null,
    startedAt = Instant.parse("2026-10-20T10:00:00Z"),
    elapsed = Duration.ofMinutes(20),
    distance = Distance(3_000),
    route = emptyList(),
    unstable = emptyList(),
    heartRates = emptyList(),
    savedAt = Instant.parse("2026-10-20T10:20:00Z")
)

/** 다른 스레드(그리기·파일 쓰기)에서 끝나는 상태 변화를 기다린다. */
fun eventually(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
    val end = System.currentTimeMillis() + timeoutMillis
    while (!condition()) {
        check(System.currentTimeMillis() < end) { "시간 안에 조건이 참이 되지 않았다" }
        Thread.sleep(10)
    }
}
