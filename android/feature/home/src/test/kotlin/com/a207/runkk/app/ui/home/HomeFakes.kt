package com.a207.runkk.app.ui.home

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
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
import com.a207.runkk.app.domain.record.TodayPlan
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.PendingRunRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

val sampleDashboard = Dashboard(
    today = Distance(3_020),
    total = Distance(128_400),
    landmark = "한강 다리 12개",
    streakDays = 4,
    grass = mapOf(LocalDate.of(2026, 10, 19) to Distance(5_000)),
    weeklyAchievement = 0.6,
    crew = null
)

class FakeRecordRepository : RecordRepository {
    var dashboardResult: Outcome<Dashboard> = Outcome.Success(sampleDashboard)
    var todayPlanResult: Outcome<TodayPlan?> = Outcome.Success(TodayPlan(Distance(5_000), Pace(330)))
    val dashboardDates = mutableListOf<LocalDate>()

    override suspend fun dashboard(baseDate: LocalDate): Outcome<Dashboard> {
        dashboardDates += baseDate
        return dashboardResult
    }

    override suspend fun runs(ownerId: Long, from: LocalDate, to: LocalDate): Outcome<RecordList> = unused()

    override suspend fun run(recordId: String): Outcome<RecordDetail> = unused()

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

class FakePendingRunRepository : PendingRunRepository {
    val pendingRuns = MutableStateFlow<List<CompletedRun>>(emptyList())

    override suspend fun save(run: CompletedRun) = Outcome.Success(Unit)

    override suspend fun pending(): Outcome<List<CompletedRun>> = Outcome.Success(pendingRuns.value)

    override fun observePending(): Flow<List<CompletedRun>> = pendingRuns

    override suspend fun remove(recordId: String) = Outcome.Success(Unit)

    override suspend fun purgeRoutes() = Outcome.Success(Unit)

    override suspend fun purgeHeartRates() = Outcome.Success(Unit)

    override suspend fun needsReminder(now: Instant) = Outcome.Success(false)

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>) = Outcome.Success(Unit)

    override suspend fun clear() = Outcome.Success(Unit)
}

fun completedRun(id: String) = CompletedRun(
    recordId = id,
    mode = RunMode.FREE,
    cardKind = CardKind.FREE_RUN,
    battleId = null,
    crewRunPostId = null,
    startedAt = Instant.parse("2026-10-19T10:00:00Z"),
    elapsed = Duration.ofMinutes(27),
    distance = Distance(5_020),
    route = emptyList(),
    unstable = emptyList(),
    heartRates = emptyList(),
    savedAt = Instant.parse("2026-10-19T10:27:00Z")
)

class FakeAccountRepository : com.a207.runkk.app.domain.account.AccountRepository {
    var profileResult: Outcome<com.a207.runkk.app.domain.account.Profile> = Outcome.Success(
        com.a207.runkk.app.domain.account.Profile(7, "달리는곰", "SELF", "골드", 3, emptySet(), null, null, null, null, null)
    )
    val profileIds = mutableListOf<Long>()

    override suspend fun profile(memberId: Long): Outcome<com.a207.runkk.app.domain.account.Profile> {
        profileIds += memberId
        return profileResult
    }

    override suspend fun login(token: com.a207.runkk.app.domain.account.ProviderToken) =
        unusedOutcome<com.a207.runkk.app.domain.account.SessionTokens>()

    override suspend fun signUp(form: com.a207.runkk.app.domain.account.SignUpForm) =
        unusedOutcome<com.a207.runkk.app.domain.account.SessionTokens>()

    override suspend fun logout() = unusedOutcome<Unit>()

    override suspend fun withdraw() = unusedOutcome<Unit>()

    override suspend fun editProfile(nickname: String, intro: String?, tags: Set<String>) = unusedOutcome<Unit>()

    override suspend fun changeSetting(item: String, value: String) = unusedOutcome<Unit>()

    override suspend fun giveConsent(item: String) = unusedOutcome<Unit>()

    override suspend fun withdrawConsent(item: String) = unusedOutcome<Unit>()

    override suspend fun legalDocument(type: String) = unusedOutcome<String>()

    override suspend fun settings() = unusedOutcome<com.a207.runkk.app.domain.account.MemberSettings>()
}

class FakeAvatarRepository : com.a207.runkk.app.domain.avatar.AvatarRepository {
    var avatarResult: Outcome<com.a207.runkk.app.domain.avatar.Avatar> = Outcome.Success(
        com.a207.runkk.app.domain.avatar.Avatar(null, "D1", "NORMAL", "DAY", emptyMap(), 1_250, true, emptyMap())
    )

    override suspend fun avatar() = avatarResult

    override suspend fun createFromSelfie(jpeg: ByteArray) = unusedOutcome<com.a207.runkk.app.domain.avatar.Avatar>()

    override suspend fun chooseDefault(characterId: String) = unusedOutcome<com.a207.runkk.app.domain.avatar.Avatar>()

    override suspend fun equip(changes: Map<com.a207.runkk.app.domain.avatar.ItemSlot, Long?>) =
        unusedOutcome<com.a207.runkk.app.domain.avatar.Avatar>()

    override suspend fun shop(slot: com.a207.runkk.app.domain.avatar.ItemSlot?) =
        unusedOutcome<com.a207.runkk.app.domain.avatar.Shop>()

    override suspend fun purchase(itemId: Long) = unusedOutcome<Int>()

    var faceResult: Outcome<List<com.a207.runkk.app.core.model.FaceLayer>> = Outcome.Success(emptyList())
    val faceKeys = mutableListOf<String?>()

    override suspend fun faceLayers(faceKey: String?): Outcome<List<com.a207.runkk.app.core.model.FaceLayer>> {
        faceKeys += faceKey
        return faceResult
    }
}

fun <T> unusedOutcome(): Outcome<T> = Outcome.Failure(AppError.Rejected("UNUSED"))
