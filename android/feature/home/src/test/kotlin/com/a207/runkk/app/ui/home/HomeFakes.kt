package com.a207.runkk.app.ui.home

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.account.SessionRepository
import com.a207.runkk.app.domain.account.SessionTokens
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
import com.a207.runkk.app.domain.run.LateHeartRates
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.GuideCopy
import com.a207.runkk.app.domain.settings.GuideRemote
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
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
    streakWeeks = 4,
    grass = mapOf(LocalDate.of(2026, 10, 19) to Distance(5_000)),
    weeklyAchievement = 0.6,
    crew = null,
    weekly = com.a207.runkk.app.domain.record.WeeklyGoalProgress(
        goalRuns = 4,
        goalDistance = Distance(20_000),
        actualRuns = 2,
        actualDistance = Distance(12_000),
        runsPercent = 50,
        distancePercent = 60
    ),
    rewardBalance = 1_250
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

    override suspend fun delete(recordId: String): Outcome<Unit> = unused()

    override suspend fun growth(period: AggregationPeriod): Outcome<Growth> = unused()

    override suspend fun report(): Outcome<Report> = unused()

    override suspend fun personalRanking(period: AggregationPeriod): Outcome<Ranking> = unused()

    override suspend fun crewRanking(period: AggregationPeriod): Outcome<Ranking> = unused()

    override suspend fun setWeeklyGoal(distance: Distance, runs: Int): Outcome<Unit> = unused()

    override suspend fun putPlan(date: LocalDate, distance: Distance, pace: Pace?): Outcome<Unit> = unused()

    override suspend fun deletePlan(date: LocalDate): Outcome<Unit> = unused()

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

    /** needsReminder가 돌려줄 값(W48). */
    var reminder: Outcome<Boolean> = Outcome.Success(false)

    /** needsReminder가 받은 시각. */
    val reminderChecks = mutableListOf<Instant>()

    override suspend fun needsReminder(now: Instant): Outcome<Boolean> {
        reminderChecks += now
        return reminder
    }

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>) = Outcome.Success(Unit)

    override suspend fun pendingLateHeartRates(): Outcome<List<LateHeartRates>> = Outcome.Success(emptyList())

    override suspend fun removeLateHeartRates(sent: LateHeartRates) = Outcome.Success(Unit)

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
    /** 홈 상단은 본인 요약만 읽는다(D-331, R35). 프로필 전체를 읽으면 profileIds에 남는다. */
    var summaryResult: Outcome<com.a207.runkk.app.domain.account.MySummary> = Outcome.Success(
        com.a207.runkk.app.domain.account.MySummary("달리는곰", "골드", 3, null, null, 12)
    )
    var summaryCalls = 0
    val profileIds = mutableListOf<Long>()

    override suspend fun mySummary(): Outcome<com.a207.runkk.app.domain.account.MySummary> {
        summaryCalls++
        return summaryResult
    }

    override suspend fun profile(memberId: Long): Outcome<com.a207.runkk.app.domain.account.Profile> {
        profileIds += memberId
        return unusedOutcome()
    }

    override suspend fun login(token: com.a207.runkk.app.domain.account.ProviderToken) =
        unusedOutcome<com.a207.runkk.app.domain.account.SessionTokens>()

    override suspend fun signUp(form: com.a207.runkk.app.domain.account.SignUpForm) =
        unusedOutcome<com.a207.runkk.app.domain.account.SessionTokens>()

    override suspend fun logout() = unusedOutcome<Unit>()

    override suspend fun issueWatchSession() = unusedOutcome<SessionTokens>()

    override suspend fun revokeWatchSession() = unusedOutcome<Unit>()

    override fun selfMemberId(): Long? = null

    override suspend fun startAsGuest(consents: Map<String, Boolean>) =
        unusedOutcome<com.a207.runkk.app.domain.account.GuestSession>()

    override suspend fun linkSocialAccount(token: com.a207.runkk.app.domain.account.ProviderToken) =
        unusedOutcome<Unit>()

    override suspend fun withdraw() = unusedOutcome<Unit>()

    override suspend fun editProfile(edit: com.a207.runkk.app.domain.account.ProfileEdit) = unusedOutcome<Unit>()

    val changedSettings = mutableListOf<Pair<String, String>>()

    override suspend fun changeSetting(item: String, value: String): Outcome<Unit> {
        changedSettings += item to value
        return unusedOutcome()
    }

    val consents = mutableListOf<String>()
    var consentResult: Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun giveConsent(item: String): Outcome<Unit> {
        consents += item
        return consentResult
    }

    override suspend fun withdrawConsent(item: String) = unusedOutcome<Unit>()

    override suspend fun legalDocument(type: String) = unusedOutcome<String>()

    var settingsResult: Outcome<com.a207.runkk.app.domain.account.MemberSettings> = unusedOutcome()
    var settingsCalls = 0

    override suspend fun settings(): Outcome<com.a207.runkk.app.domain.account.MemberSettings> {
        settingsCalls++
        return settingsResult
    }

    /** 배치 진행(GET /me/placement, D-493). 기본은 못 읽음이다. */
    var placementResult: Outcome<com.a207.runkk.app.domain.account.PlacementProgress> =
        Outcome.Failure(AppError.Network)
    var placementCalls = 0

    override suspend fun placement(): Outcome<com.a207.runkk.app.domain.account.PlacementProgress> {
        placementCalls++
        return placementResult
    }
}

fun <T> unusedOutcome(): Outcome<T> = Outcome.Failure(AppError.Rejected("UNUSED"))

/** 설정 사본(위치 동의·가이드 사본). 값을 바꾸면 changes()가 바로 방출한다. refresh가 [consentAfterRefresh]를 사본에 반영한다. */
class FakeSettingsRepository : SettingsRepository {
    private val snapshot = MutableStateFlow(SettingsSnapshot.SAFE)

    var guidance: GuidanceSettings
        get() = snapshot.value.guidanceSettings
        set(value) {
            snapshot.value = snapshot.value.copy(guidanceSettings = value)
        }

    var isLocationConsented = false
    var consentAfterRefresh = true
    var refreshCalls = 0

    override suspend fun refresh(): Outcome<Unit> {
        refreshCalls++
        isLocationConsented = consentAfterRefresh
        return Outcome.Success(Unit)
    }

    override fun locationConsented() = isLocationConsented

    override fun heartRateConsented() = false

    override fun watchLinked() = false

    override fun courseMapShared() = false

    override fun birthYear(): Int? = null

    override fun guidanceSettings() = guidance

    override fun isGuest(): Boolean = false

    override fun changes(): Flow<SettingsSnapshot> = snapshot

    override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)

    var guide: GuideCopy = GuideCopy.NONE

    /** 참이면 사본 쓰기가 실패한다(저장소 오류). */
    var isGuideSaveFailing = false

    override suspend fun guideCopy(): GuideCopy = guide

    override suspend fun saveGuideCopy(copy: GuideCopy): Outcome<Unit> {
        if (isGuideSaveFailing) return Outcome.Failure(com.a207.runkk.app.core.model.AppError.Storage)
        guide = copy
        return Outcome.Success(Unit)
    }
}

/** 첫 사용 가이드 서버 값(GET·PUT /me/guide). */
class FakeGuideRemote : GuideRemote {
    var seen: Outcome<Boolean> = Outcome.Success(false)
    var seenCalls = 0
    var markCalls = 0

    override suspend fun seen(): Outcome<Boolean> {
        seenCalls++
        return seen
    }

    override suspend fun markSeen(): Outcome<Unit> {
        markCalls++
        return Outcome.Success(Unit)
    }
}

/** 로그인 상태만 쓰는 세션. */
class FakeSessionRepository(initial: Boolean = true) : SessionRepository {
    override val loggedIn = MutableStateFlow(initial)

    override suspend fun save(tokens: SessionTokens) = unusedOutcome<Unit>()

    override suspend fun clear() = unusedOutcome<Unit>()

    override suspend fun saveGuestCredential(credential: String) = unusedOutcome<Unit>()

    override suspend fun clearGuestCredential() = unusedOutcome<Unit>()

    override suspend fun hasGuestCredential() = false
}
