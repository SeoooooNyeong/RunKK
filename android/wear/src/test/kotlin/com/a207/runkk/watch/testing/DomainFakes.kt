package com.a207.runkk.watch.testing

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.GuestSession
import com.a207.runkk.app.domain.account.MemberSettings
import com.a207.runkk.app.domain.account.MySummary
import com.a207.runkk.app.domain.account.Profile
import com.a207.runkk.app.domain.account.ProfileEdit
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.SessionRepository
import com.a207.runkk.app.domain.account.SessionTokens
import com.a207.runkk.app.domain.account.SignUpForm
import com.a207.runkk.app.domain.battle.ActiveBattle
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.FriendInvite
import com.a207.runkk.app.domain.battle.FriendInviteView
import com.a207.runkk.app.domain.battle.MatchAcceptance
import com.a207.runkk.app.domain.battle.OpponentTrack
import com.a207.runkk.app.domain.battle.RecordBattleCandidates
import com.a207.runkk.app.domain.battle.RecordBattleKind
import com.a207.runkk.app.domain.battle.RecordBattleStart
import com.a207.runkk.app.domain.battle.RecordTrack
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceOutput
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LateHeartRates
import com.a207.runkk.app.domain.run.LocationPermission
import com.a207.runkk.app.domain.run.MeasurementHost
import com.a207.runkk.app.domain.run.MeasurementRuntime
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.RunArchive
import com.a207.runkk.app.domain.run.RunCoach
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.app.domain.run.RunWatchBridge
import com.a207.runkk.app.domain.run.UploadScheduler
import com.a207.runkk.app.domain.run.WatchGateway
import com.a207.runkk.app.domain.settings.GuidanceSettings
import com.a207.runkk.app.domain.settings.SettingsRepository
import com.a207.runkk.app.domain.settings.SettingsSnapshot
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope

/** 서버 설정 사본(가짜). 기본은 위치·심박 동의, 워치연동 켬. */
class FakeSettingsRepository(
    var isLocationConsented: Boolean = true,
    var isHeartRateConsented: Boolean = true,
    var isWatchLinked: Boolean = true,
    var guidance: GuidanceSettings = GuidanceSettings.DEFAULT
) : SettingsRepository {
    var refreshes: Int = 0
        private set
    var clears: Int = 0
        private set

    override suspend fun refresh(): Outcome<Unit> {
        refreshes++
        return Outcome.Success(Unit)
    }

    override fun locationConsented(): Boolean = isLocationConsented

    override fun heartRateConsented(): Boolean = isHeartRateConsented

    override fun watchLinked(): Boolean = isWatchLinked

    override fun courseMapShared(): Boolean = false

    override fun birthYear(): Int? = null

    override fun guidanceSettings(): GuidanceSettings = guidance

    override fun isGuest(): Boolean = false

    override fun changes(): Flow<SettingsSnapshot> = emptyFlow()

    override suspend fun clear(): Outcome<Unit> {
        clears++
        return Outcome.Success(Unit)
    }
}

/** 미전송 기록 보관함(가짜). [clear]는 [events]에 남기고, [isClearFailing]이면 Storage 실패다. */
class FakePendingRunRepository(private val events: MutableList<String> = mutableListOf()) : PendingRunRepository {
    val saved = mutableListOf<CompletedRun>()
    var isClearFailing: Boolean = false

    /** clear 순간의 다른 저장소 상태를 사건 이름에 붙인다(H-56 순서 확인). */
    var describeOnClear: () -> String = { "" }

    override suspend fun save(run: CompletedRun): Outcome<Unit> {
        saved += run
        return Outcome.Success(Unit)
    }

    override suspend fun pending(): Outcome<List<CompletedRun>> = Outcome.Success(saved.toList())

    override fun observePending(): Flow<List<CompletedRun>> = flowOf(saved.toList())

    override suspend fun remove(recordId: String): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun purgeRoutes(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun purgeHeartRates(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun needsReminder(now: Instant): Outcome<Boolean> = Outcome.Success(false)

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>): Outcome<Unit> =
        Outcome.Success(Unit)

    override suspend fun pendingLateHeartRates(): Outcome<List<LateHeartRates>> = Outcome.Success(emptyList())

    override suspend fun removeLateHeartRates(sent: LateHeartRates): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun clear(): Outcome<Unit> {
        events += "pendingRuns.clear${describeOnClear()}"
        if (isClearFailing) return Outcome.Failure(AppError.Storage)
        saved.clear()
        return Outcome.Success(Unit)
    }
}

class FakeUploadScheduler : UploadScheduler {
    var requests: Int = 0
        private set

    override fun requestUpload() {
        requests++
    }
}

/** 토큰 저장소(가짜 EncryptedTokenStore). 저장·삭제를 [events]에 남긴다. */
class FakeSessionRepository(private val events: MutableList<String> = mutableListOf()) : SessionRepository {
    private val _loggedIn = MutableStateFlow(false)
    override val loggedIn: StateFlow<Boolean> = _loggedIn
    var tokens: SessionTokens? = null
        private set
    var describeOnClear: () -> String = { "" }

    override suspend fun save(tokens: SessionTokens): Outcome<Unit> {
        events += "tokens.save"
        this.tokens = tokens
        _loggedIn.value = true
        return Outcome.Success(Unit)
    }

    override suspend fun clear(): Outcome<Unit> {
        events += "tokens.clear${describeOnClear()}"
        tokens = null
        _loggedIn.value = false
        return Outcome.Success(Unit)
    }

    /** 공용 :data TokenAuthenticator가 갱신을 거부당해 지운 경우(상태도 2.4.26 "갱신 실패"). */
    fun expire() {
        tokens = null
        _loggedIn.value = false
    }

    override suspend fun saveGuestCredential(credential: String): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun clearGuestCredential(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun hasGuestCredential(): Boolean = false
}

/** 계정 REST(가짜). 워치는 logout·mySummary(티어, 성별)만 부른다. */
class FakeAccountRepository(private val events: MutableList<String> = mutableListOf()) : AccountRepository {
    var logouts: Int = 0
        private set

    /** mySummary가 돌려줄 본인 요약. null이면 Network 실패. */
    var summary: MySummary? = MySummary("러너", "SILVER", 3, null, null, 10)
    val gender = MutableStateFlow<Gender?>(null)
    override val myGender: StateFlow<Gender?> = gender

    override suspend fun logout(): Outcome<Unit> {
        logouts++
        events += "accounts.logout"
        return Outcome.Failure(AppError.Network)
    }

    override suspend fun login(token: ProviderToken): Outcome<SessionTokens> = unused()

    override suspend fun signUp(form: SignUpForm): Outcome<SessionTokens> = unused()

    override suspend fun issueWatchSession(): Outcome<SessionTokens> = unused()

    override suspend fun startAsGuest(consents: Map<String, Boolean>): Outcome<GuestSession> = unused()

    override suspend fun linkSocialAccount(token: ProviderToken): Outcome<Unit> = unused()

    override suspend fun withdraw(): Outcome<Unit> = unused()

    override suspend fun profile(memberId: Long): Outcome<Profile> = unused()

    override suspend fun mySummary(): Outcome<MySummary> =
        summary?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.Network)

    override suspend fun editProfile(edit: ProfileEdit): Outcome<Unit> = unused()

    override suspend fun changeSetting(item: String, value: String): Outcome<Unit> = unused()

    override suspend fun giveConsent(item: String): Outcome<Unit> = unused()

    override suspend fun withdrawConsent(item: String): Outcome<Unit> = unused()

    override suspend fun legalDocument(type: String): Outcome<String> = unused()

    override suspend fun settings(): Outcome<MemberSettings> = unused()
}

/** 대결 REST(가짜). 워치 캐시는 recordTracks만 부른다. [tracks]가 없는 종류는 Network 실패다. */
class FakeBattleRepository : BattleRepository {
    val tracks = mutableMapOf<RecordBattleKind, List<RecordTrack>>()
    val calls = mutableListOf<RecordBattleKind>()

    /** 있으면 recordTracks가 이 신호를 받을 때까지 멈춘다(받는 동안 다른 일이 끼어드는 경우). */
    var gate: CompletableDeferred<Unit>? = null

    /** 매칭 요청 응답(null이면 Network 실패)과 요청한 거리. */
    var matchAnswer: Outcome<MatchAcceptance> = Outcome.Success(MatchAcceptance(isWaiting = true, distance = null))
    val matchRequests = mutableListOf<Distance>()
    var cancels: Int = 0
        private set

    /** 서버 기록 대결 시작 응답(셀프·셀럽 공통). 없으면 Network 실패다. */
    var recordStart: RecordBattleStart? = null
    val forfeits = mutableListOf<Long>()

    /** 대결 결과 응답. */
    var results: MutableMap<Long, BattleResult> = mutableMapOf()

    override suspend fun recordTracks(kind: RecordBattleKind): Outcome<List<RecordTrack>> {
        calls += kind
        gate?.await()
        return tracks[kind]?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.Network)
    }

    override suspend fun matchStatus(): Outcome<MatchAcceptance> = unused()

    override suspend fun requestMatch(distance: Distance): Outcome<MatchAcceptance> {
        matchRequests += distance
        return matchAnswer
    }

    override suspend fun cancel(): Outcome<MatchAcceptance> {
        cancels++
        return Outcome.Success(MatchAcceptance(isWaiting = false, distance = null))
    }

    override suspend fun selfCandidates(): Outcome<RecordBattleCandidates> = unused()

    override suspend fun celebrityCandidates(distance: Distance?): Outcome<RecordBattleCandidates> = unused()

    override suspend fun startSelfBattle(distance: Distance): Outcome<RecordBattleStart> =
        recordStart?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.Network)

    override suspend fun startCelebrityBattle(celebrityId: Long): Outcome<RecordBattleStart> =
        recordStart?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.Network)

    override suspend fun markStarted(battleId: Long): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun forfeit(battleId: Long): Outcome<Unit> {
        forfeits += battleId
        return Outcome.Success(Unit)
    }

    override suspend fun cancel(battleId: Long): Outcome<Unit> = unused()

    override suspend fun active(): Outcome<ActiveBattle?> = unused()

    override suspend fun result(battleId: Long): Outcome<BattleResult> =
        results[battleId]?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.Network)

    override suspend fun history(filter: BattleHistory.Type?, cursor: Long?): Outcome<BattleHistory> = unused()

    override suspend fun createFriendInvite(distance: Distance): Outcome<FriendInvite> = unused()

    override suspend fun myFriendInvite(): Outcome<FriendInvite?> = unused()

    override suspend fun cancelFriendInvite(inviteId: Long): Outcome<Unit> = unused()

    override suspend fun friendInvite(token: String): Outcome<FriendInviteView> = unused()

    override suspend fun acceptFriendInvite(token: String): Outcome<Long> = unused()

    override suspend fun declineFriendInvite(token: String): Outcome<Unit> = unused()
}

class FakeGuidanceOutput : GuidanceOutput {
    val played = mutableListOf<Guidance>()

    override fun play(g: Guidance) {
        played += g
    }
}

class FakeLocationPermission(var isGranted: Boolean = true) : LocationPermission {
    override fun granted(): Boolean = isGranted
}

/** 측정 서비스 시작·정지(가짜 MeasurementHost). */
class FakeMeasurementHost : MeasurementHost {
    var starts: Int = 0
        private set
    var stops: Int = 0
        private set

    override fun startForeground(): Outcome<Unit> {
        starts++
        return Outcome.Success(Unit)
    }

    override fun stop() {
        stops++
    }
}

/** domain RunTracker를 가짜 포트로 조립한다(워치 Hilt 그래프와 같은 협력 객체 넷, D-366). */
fun TestScope.runTracker(
    gateway: WatchGateway,
    host: MeasurementHost = FakeMeasurementHost(),
    settings: SettingsRepository = FakeSettingsRepository(),
    pendingRuns: PendingRunRepository = FakePendingRunRepository()
): RunTracker = RunTracker(
    RunArchive(pendingRuns, FakeUploadScheduler(), backgroundScope),
    RunWatchBridge(gateway, backgroundScope),
    RunCoach(FakeGuidanceOutput(), settings),
    MeasurementRuntime(FakeLocationPermission(), host, settings, SchedulerClock(testScheduler))
)

/** 셀프런 3km 15분 균등 페이스 상대 기록. */
fun selfTrack(distanceMeters: Long = 3_000, finishSeconds: Long = 900): RecordTrack = RecordTrack(
    kind = RecordBattleKind.SELF,
    distance = Distance(distanceMeters),
    celebrityId = null,
    displayName = "내 기록",
    finishTime = Duration.ofSeconds(finishSeconds),
    sourceLabel = null,
    celebrityFigure = null,
    track = OpponentTrack.uniform(Distance(distanceMeters), Duration.ofSeconds(finishSeconds))
)

/** 셀럽런 상대 기록. */
fun celebrityTrack(id: Long, distanceMeters: Long, finishSeconds: Long): RecordTrack = RecordTrack(
    kind = RecordBattleKind.CELEBRITY,
    distance = Distance(distanceMeters),
    celebrityId = id,
    displayName = "셀럽$id",
    finishTime = Duration.ofSeconds(finishSeconds),
    sourceLabel = "출처$id",
    celebrityFigure = "figure$id",
    track = OpponentTrack.uniform(Distance(distanceMeters), Duration.ofSeconds(finishSeconds))
)

private fun unused(): Nothing = error("워치 시험에서 쓰지 않는 연산")
