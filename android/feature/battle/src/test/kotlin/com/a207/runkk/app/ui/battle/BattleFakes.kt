package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.GuestSession
import com.a207.runkk.app.domain.account.LocationConsentUseCase
import com.a207.runkk.app.domain.account.MemberSettings
import com.a207.runkk.app.domain.account.MySummary
import com.a207.runkk.app.domain.account.PlacementProgress
import com.a207.runkk.app.domain.account.PlacementRound
import com.a207.runkk.app.domain.account.PlacementStatus
import com.a207.runkk.app.domain.account.Profile
import com.a207.runkk.app.domain.account.ProfileEdit
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.SessionTokens
import com.a207.runkk.app.domain.account.SignUpForm
import com.a207.runkk.app.domain.battle.ActiveBattle
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleRecordUploads
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import com.a207.runkk.app.domain.battle.FriendInvite
import com.a207.runkk.app.domain.battle.FriendInviteView
import com.a207.runkk.app.domain.battle.MatchAcceptance
import com.a207.runkk.app.domain.battle.OpponentTrack
import com.a207.runkk.app.domain.battle.RecordBattleCandidates
import com.a207.runkk.app.domain.battle.RecordBattleKind
import com.a207.runkk.app.domain.battle.RecordBattleStart
import com.a207.runkk.app.domain.battle.RecordTrack
import com.a207.runkk.app.domain.live.ClientMessage
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.LiveState
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
import com.a207.runkk.app.domain.record.RecordDetail
import com.a207.runkk.app.domain.record.RunSummary
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceOutput
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LateHeartRates
import com.a207.runkk.app.domain.run.LocationPermission
import com.a207.runkk.app.domain.run.MeasurementHost
import com.a207.runkk.app.domain.run.MeasurementRuntime
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.ProximityAlert
import com.a207.runkk.app.domain.run.RunArchive
import com.a207.runkk.app.domain.run.RunCoach
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/**
 * 1초 시계처럼 끝나지 않는 ViewModel 작업이 runTest 정리(남은 작업 진행)를 막지 않게, 시험 끝에 [opened]의 범위를 닫는다.
 */
class ViewModelCloser {
    private val opened = mutableListOf<ViewModel>()

    fun <T : ViewModel> track(vm: T): T = vm.also { opened += it }

    fun closeAll() = opened.forEach { it.viewModelScope.cancel() }
}

fun MainDispatcherRule.runVmTest(body: suspend TestScope.(ViewModelCloser) -> Unit) = runTest(dispatcher) {
    val closer = ViewModelCloser()
    try {
        body(closer)
    } finally {
        closer.closeAll()
    }
}

val start: Instant = Instant.parse("2026-10-20T10:00:00Z")

class MutableClock(var now: Instant = start) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now
}

/** 대결 REST 가짜. 부른 연산을 [calls]에 남긴다. */
class FakeBattleRepository : BattleRepository {
    val calls = mutableListOf<String>()
    var match: Outcome<MatchAcceptance> = Outcome.Success(MatchAcceptance(isWaiting = true, distance = Distance(5_000)))
    var cancelResult: Outcome<MatchAcceptance> = Outcome.Success(MatchAcceptance(isWaiting = false, distance = null))
    var selfResult: Outcome<RecordBattleCandidates> = Outcome.Success(RecordBattleCandidates(selfCandidates))
    var celebResult: Outcome<RecordBattleCandidates> = Outcome.Success(RecordBattleCandidates(celebCandidates))
    var startResult: Outcome<RecordBattleStart>? = null
    var resultResult: Outcome<BattleResult> = Outcome.Success(battleResult())
    var historyResult: Outcome<BattleHistory> = Outcome.Success(history())

    /** GET /battles/match 응답(기본 대결 거리 5km, 대기 아님). [calls]와 따로 센다. */
    var status: Outcome<MatchAcceptance> =
        Outcome.Success(MatchAcceptance(isWaiting = false, distance = null, defaultDistance = Distance(5_000)))
    var statusCalls = 0

    override suspend fun matchStatus(): Outcome<MatchAcceptance> {
        statusCalls++
        return status
    }

    override suspend fun requestMatch(distance: Distance): Outcome<MatchAcceptance> {
        calls += "match:${distance.meters}"
        return match
    }

    /** 있으면 취소 응답을 이 신호까지 늦춘다(성사와 취소의 경쟁 시험). */
    var cancelGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun cancel(): Outcome<MatchAcceptance> {
        calls += "cancel"
        cancelGate?.await()
        return cancelResult
    }

    override suspend fun selfCandidates(): Outcome<RecordBattleCandidates> {
        calls += "self"
        return selfResult
    }

    override suspend fun celebrityCandidates(distance: Distance?): Outcome<RecordBattleCandidates> {
        calls += "celeb:${distance?.meters}"
        return celebResult
    }

    override suspend fun recordTracks(kind: RecordBattleKind): Outcome<List<RecordTrack>> =
        Outcome.Failure(AppError.Rejected("UNUSED"))

    override suspend fun celebrityTrack(celebrityId: Long): Outcome<RecordTrack> =
        Outcome.Failure(AppError.Rejected("UNUSED"))

    override suspend fun startSelfBattle(distance: Distance): Outcome<RecordBattleStart> {
        calls += "startSelf:${distance.meters}"
        return startResult ?: Outcome.Success(recordStart(distance, CardKind.SELF_RUN, "과거의 나"))
    }

    override suspend fun startCelebrityBattle(celebrityId: Long): Outcome<RecordBattleStart> {
        calls += "startCeleb:$celebrityId"
        return startResult ?: Outcome.Success(recordStart(Distance(10_000), CardKind.CELEB_RUN, "번개발"))
    }

    override suspend fun markStarted(battleId: Long): Outcome<Unit> {
        calls += "started:$battleId"
        return Outcome.Success(Unit)
    }

    /** forfeit 응답(그만두기 실패 시험). */
    var forfeitResult: Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun forfeit(battleId: Long): Outcome<Unit> {
        calls += "forfeit:$battleId"
        return forfeitResult
    }

    override suspend fun cancel(battleId: Long): Outcome<Unit> {
        calls += "cancel:$battleId"
        return forfeitResult
    }

    /** GET /battles/active 응답. 기본은 진행 중인 대결 없음(204). */
    var activeResult: Outcome<ActiveBattle?> = Outcome.Success(null)

    override suspend fun active(): Outcome<ActiveBattle?> {
        calls += "active"
        return activeResult
    }

    /** 차례로 줄 결과(비면 [resultResult]). 판 결과 다시 읽기 시험이 쓴다. */
    val resultQueue = ArrayDeque<Outcome<BattleResult>>()

    override suspend fun result(battleId: Long): Outcome<BattleResult> {
        calls += "result:$battleId"
        return resultQueue.removeFirstOrNull() ?: resultResult
    }

    /** 다음 쪽(커서가 있을 때)의 이력. */
    var historyMore: Outcome<BattleHistory> = Outcome.Success(
        BattleHistory(
            entries = listOf(
                BattleHistory.Entry(
                    4,
                    BattleHistory.Type.RANDOM,
                    "달빛",
                    Distance(3_000),
                    "LOSE",
                    start.minusSeconds(400_000)
                )
            ),
            total = 12,
            wins = 7,
            losses = 5,
            selfNewRecords = 2,
            nextCursor = null
        )
    )

    override suspend fun history(filter: BattleHistory.Type?, cursor: Long?): Outcome<BattleHistory> {
        if (cursor != null) {
            calls += "history:${filter?.name}:$cursor"
            return historyMore
        }
        calls += "history:${filter?.name}"
        return historyResult
    }

    var createInviteResult: Outcome<FriendInvite> = Outcome.Success(friendInvite())
    var myInviteResult: Outcome<FriendInvite?> = Outcome.Success(null)
    var cancelInviteResults = ArrayDeque<Outcome<Unit>>()
    var inviteViewResult: Outcome<FriendInviteView> = Outcome.Success(inviteView())
    var acceptResult: Outcome<Long> = Outcome.Success(9L)
    var declineResult: Outcome<Unit> = Outcome.Success(Unit)

    /** 있으면 초대 만들기 응답을 이 신호까지 늦춘다(만드는 중에 떠나는 시험). */
    var createGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun createFriendInvite(distance: Distance): Outcome<FriendInvite> {
        calls += "invite:${distance.meters}"
        createGate?.await()
        return createInviteResult
    }

    /** 있으면 남은 초대 조회 응답을 이 신호까지 늦춘다(조회가 새 초대보다 늦게 끝나는 경합 시험). */
    var myInviteGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun myFriendInvite(): Outcome<FriendInvite?> {
        calls += "myInvite"
        myInviteGate?.await()
        return myInviteResult
    }

    override suspend fun cancelFriendInvite(inviteId: Long): Outcome<Unit> {
        calls += "cancelInvite:$inviteId"
        return cancelInviteResults.removeFirstOrNull() ?: Outcome.Success(Unit)
    }

    override suspend fun friendInvite(token: String): Outcome<FriendInviteView> {
        calls += "inviteView:$token"
        return inviteViewResult
    }

    override suspend fun acceptFriendInvite(token: String): Outcome<Long> {
        calls += "accept:$token"
        return acceptResult
    }

    override suspend fun declineFriendInvite(token: String): Outcome<Unit> {
        calls += "decline:$token"
        return declineResult
    }
}

/** 내가 만든 초대(5km, 만료 = 시작 + 5분). */
fun friendInvite(inviteId: Long = 41) = FriendInvite(
    inviteId = inviteId,
    token = "aB3-_xY0aB3-_xY0aB3-_x",
    inviteUrl = "https://k15a207.p.ssafy.io/invite/battle/aB3-_xY0aB3-_xY0aB3-_x",
    distance = Distance(5_000),
    expiresAt = start.plusSeconds(300)
)

/** 받은 초대(초대한 "바람처럼" GOLD 29:48, 나 UNRANKED 30:12). */
fun inviteView(blockedReason: String? = null) = FriendInviteView(
    inviteId = 41,
    status = "PENDING",
    distance = Distance(5_000),
    expiresAt = start.plusSeconds(300),
    inviter = FriendInviteView.Inviter("바람처럼", "GOLD", 1_788),
    myTier = "UNRANKED",
    myBestSeconds = 1_812,
    blockedReason = blockedReason
)

/**
 * 계정 가짜: 본인 요약(닉네임·티어·유효 러닝 수, D-331)과 위치 동의 주기만 쓴다. 배틀런은 프로필 전체를 읽지 않는다(R53):
 * profile()은 실패를 돌려주고 부른 횟수를 센다.
 */
class FakeAccounts : AccountRepository {
    val consents = mutableListOf<String>()
    var consentResult: Outcome<Unit> = Outcome.Success(Unit)
    var summaryResult: Outcome<MySummary> =
        Outcome.Success(MySummary("달리는곰", "GOLD", 3, null, null, 12))
    var profileCalls = 0

    /** 있으면 요약 응답을 이 신호까지 늦춘다(초대 응답이 먼저 오는 경합 시험). */
    var summaryGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun mySummary(): Outcome<MySummary> {
        summaryGate?.await()
        return summaryResult
    }

    override suspend fun profile(memberId: Long): Outcome<Profile> {
        profileCalls++
        return unused()
    }

    override suspend fun giveConsent(item: String): Outcome<Unit> {
        consents += item
        return consentResult
    }

    override suspend fun login(token: ProviderToken): Outcome<SessionTokens> = unused()

    override suspend fun signUp(form: SignUpForm): Outcome<SessionTokens> = unused()

    override suspend fun logout(): Outcome<Unit> = unused()

    override suspend fun issueWatchSession(): Outcome<SessionTokens> = unused()

    override suspend fun revokeWatchSession(): Outcome<Unit> = unused()

    override fun selfMemberId(): Long? = null

    override suspend fun startAsGuest(consents: Map<String, Boolean>): Outcome<GuestSession> = unused()

    override suspend fun linkSocialAccount(token: ProviderToken): Outcome<Unit> = unused()

    override suspend fun withdraw(): Outcome<Unit> = unused()

    override suspend fun editProfile(edit: ProfileEdit): Outcome<Unit> = unused()

    override suspend fun changeSetting(item: String, value: String): Outcome<Unit> = unused()

    override suspend fun withdrawConsent(item: String): Outcome<Unit> = unused()

    override suspend fun legalDocument(type: String): Outcome<String> = unused()

    override suspend fun settings(): Outcome<MemberSettings> = unused()

    /** 본인 배치 진행(GET /me/placement). 차례로 줄 값이 [placementQueue]에 있으면 그것부터 쓴다. */
    var placementResult: Outcome<PlacementProgress> = Outcome.Failure(AppError.Server(0))
    val placementQueue = ArrayDeque<Outcome<PlacementProgress>>()
    var placementCalls = 0

    override suspend fun placement(): Outcome<PlacementProgress> {
        placementCalls++
        return placementQueue.removeFirstOrNull() ?: placementResult
    }

    /** 배치고사 완료 화면을 본 마지막 판(기기 사본 가짜, D-531). */
    var placementSeen: Long? = null

    override suspend fun placementCompleteSeen(): Long? = placementSeen

    override suspend fun savePlacementCompleteSeen(battleId: Long): Outcome<Unit> {
        placementSeen = battleId
        return Outcome.Success(Unit)
    }

    private fun <T> unused(): Outcome<T> = Outcome.Failure(AppError.Rejected("UNUSED"))
}

/** 위치 동의 사본을 바꿀 수 있는 설정 가짜. refresh가 [consentAfterRefresh]를 사본에 반영한다. */
class FakeConsentSettings(var isConsented: Boolean = true) : SettingsRepository {
    var consentAfterRefresh = true

    override suspend fun refresh(): Outcome<Unit> {
        isConsented = consentAfterRefresh
        return Outcome.Success(Unit)
    }

    override fun locationConsented() = isConsented

    override fun heartRateConsented() = false

    override fun watchLinked() = false

    override fun courseMapShared() = false

    override fun birthYear(): Int? = null

    override fun guidanceSettings() = GuidanceSettings.DEFAULT

    override fun isGuest(): Boolean = false

    override fun changes(): Flow<SettingsSnapshot> = flowOf(SettingsSnapshot.SAFE)

    override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
}

/** 위치 동의 UseCase를 가짜 포트로 조립한다. */
fun consentOf(settings: FakeConsentSettings = FakeConsentSettings(), accounts: FakeAccounts = FakeAccounts()) =
    LocationConsentUseCase(accounts, settings)

/** 실시간 채널 가짜: [push]로 서버 메시지를 넣는다. 닫히기 전까지 같은 채널을 돌려준다(D-57). */
class FakeLiveChannels : LiveChannelProvider {
    val channel = FakeLiveChannel()
    var opened = 0

    override fun open(): LiveChannel {
        opened++
        return channel
    }
}

class FakeLiveChannel : LiveChannel {
    val sent = mutableListOf<ProgressReport>()
    var connects = 0
    var closed = 0
    var connectFailure: AppError? = null

    /** null은 메시지 흐름의 끝(채널 종료)이다. */
    private val flow = MutableSharedFlow<ServerMessage?>(extraBufferCapacity = 64)

    /** 있으면 connect가 이것이 끝날 때까지 기다린다(핸드셰이크가 늦은 연결). */
    var connectGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun connect(): Outcome<Unit> {
        connects++
        connectGate?.await()
        return connectFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }

    override fun send(m: ClientMessage) {
        if (m is ProgressReport) sent += m
    }

    /** 연결 상태. 시험이 바꿔 끊김·재연결을 흉내 낸다. */
    override val state: MutableStateFlow<LiveState> = MutableStateFlow(LiveState.CONNECTED)

    override val messages: Flow<ServerMessage> = flow.takeWhile { it != null }.filterNotNull()

    override fun close() {
        closed++
    }

    fun push(m: ServerMessage) {
        check(flow.tryEmit(m))
    }

    /** 메시지 흐름을 끝낸다(서버가 채널을 닫음). */
    fun endMessages() {
        check(flow.tryEmit(null))
    }
}

class FakeLocationPermission(var granted: Boolean = true) : LocationPermission {
    override fun granted() = granted
}

/** 미전송 기록 가짜. [runs]를 바꿔 기록 저장·업로드를 흉내 낸다. */
class NoPendingRuns : PendingRunRepository {
    val runs = MutableStateFlow<List<CompletedRun>>(emptyList())

    override suspend fun save(run: CompletedRun): Outcome<Unit> {
        runs.value = runs.value + run
        return Outcome.Success(Unit)
    }

    override suspend fun pending(): Outcome<List<CompletedRun>> = Outcome.Success(runs.value)

    override fun observePending(): Flow<List<CompletedRun>> = runs

    override suspend fun remove(recordId: String): Outcome<Unit> {
        runs.value = runs.value.filterNot { it.recordId == recordId }
        return Outcome.Success(Unit)
    }

    override suspend fun purgeRoutes() = Outcome.Success(Unit)

    override suspend fun purgeHeartRates() = Outcome.Success(Unit)

    override suspend fun needsReminder(now: Instant) = Outcome.Success(false)

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>) = Outcome.Success(Unit)

    override suspend fun pendingLateHeartRates(): Outcome<List<LateHeartRates>> = Outcome.Success(emptyList())

    override suspend fun removeLateHeartRates(sent: LateHeartRates) = Outcome.Success(Unit)

    override suspend fun clear() = Outcome.Success(Unit)
}

private class Silent :
    GuidanceOutput,
    UploadScheduler,
    MeasurementHost {
    override fun play(g: Guidance) = Unit

    override fun requestUpload() = Unit

    override fun startForeground(): Outcome<Unit> = Outcome.Success(Unit)

    override fun stop() = Unit
}

private class NoWatch : WatchGateway {
    override val endRequests: Flow<Unit> = MutableSharedFlow()
    override val pauseRequests: Flow<WatchGateway.PauseAction> = MutableSharedFlow()

    override fun publishProximity(alert: ProximityAlert?) = Unit

    override fun publishLeadChange(kind: Guidance.Kind) = Unit

    override val startRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val heartRates: Flow<List<HeartRateSample>> = MutableSharedFlow()

    override fun publish(d: WatchDisplay) = Unit

    override fun isConnected() = false

    var isMeasuringOnWatch = false

    override fun isWatchMeasuring(): Boolean = isMeasuringOnWatch
}

private class ConsentedSettings : SettingsRepository {
    override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)

    override fun locationConsented() = true

    override fun heartRateConsented() = false

    override fun watchLinked() = false

    override fun courseMapShared() = false

    override fun birthYear(): Int? = null

    override fun guidanceSettings() = GuidanceSettings.DEFAULT

    override fun isGuest(): Boolean = false

    override fun changes(): Flow<SettingsSnapshot> = flowOf(SettingsSnapshot.SAFE)

    override suspend fun clear(): Outcome<Unit> = Outcome.Success(Unit)
}

/** 실제 BattleRunCoordinator(+ RunTracker)를 가짜 포트로 조립한다. 범위는 runTest의 backgroundScope. */
class BattleWorld(
    scope: CoroutineScope,
    val clock: MutableClock = MutableClock(),
    val permission: FakeLocationPermission = FakeLocationPermission(),
    val battles: FakeBattleRepository = FakeBattleRepository(),
    val channels: FakeLiveChannels = FakeLiveChannels()
) {
    private val silent = Silent()
    private val watch = NoWatch()

    /** 짝지은 워치가 스스로 측정 중인가(FR-252). 시험이 바꾼다. */
    var isWatchMeasuring: Boolean
        get() = watch.isMeasuringOnWatch
        set(value) {
            watch.isMeasuringOnWatch = value
        }
    val pendingRuns = NoPendingRuns()
    val uploads = BattleRecordUploads(pendingRuns)
    val tracker =
        RunTracker(
            RunArchive(pendingRuns, silent, scope),
            RunWatchBridge(watch, scope),
            RunCoach(silent, ConsentedSettings()),
            MeasurementRuntime(permission, silent, ConsentedSettings(), clock)
        )
    val coordinator = BattleRunCoordinator(tracker, channels, battles, silent, clock, scope)

    companion object {
        /** 조정자 범위: 시험 스케줄러를 쓰는 표준 디스패처(직렬화용 limitedParallelism을 쓰려면 Unconfined가 아니어야 한다). */
        fun TestScope.world(): BattleWorld = BattleWorld(
            CoroutineScope(StandardTestDispatcher(testScheduler) + Job(backgroundScope.coroutineContext.job))
        )
    }
}

val selfCandidates = listOf(
    RecordBattleCandidates.Candidate(
        null,
        "나",
        Distance(3_000),
        Duration.ofSeconds(960),
        LocalDate.of(2026, 10, 3),
        null,
        false
    ),
    RecordBattleCandidates.Candidate(
        null,
        "나",
        Distance(5_000),
        Duration.ofSeconds(1_650),
        LocalDate.of(2026, 10, 12),
        null,
        false
    ),
    RecordBattleCandidates.Candidate(null, "나", Distance(10_000), null, null, null, false),
    RecordBattleCandidates.Candidate(null, "나", Distance(21_097), null, null, null, false)
)

/** 서버 GET /battles/celebrity/candidates 모양: sourceLabel은 Source.label()이 붙인 "공개 기록 기준 · 출처명" 그대로다(FR-100). */
val celebCandidates = listOf(
    RecordBattleCandidates.Candidate(
        7,
        "번개발",
        Distance(10_000),
        Duration.ofSeconds(2_530),
        null,
        "공개 기록 기준 · 2025 서울마라톤",
        false
    ),
    RecordBattleCandidates.Candidate(
        8,
        "마라톤왕",
        Distance(42_195),
        Duration.ofSeconds(7_800),
        null,
        "공개 기록 기준 · 2024 춘천마라톤",
        true
    )
)

fun recordStart(distance: Distance, kind: CardKind, name: String) = RecordBattleStart(
    battleId = 42,
    opponentName = name,
    distance = distance,
    track = OpponentTrack.uniform(distance, Duration.ofSeconds(1_650)),
    cardKind = kind
)

/** 아직 올라가지 않은 대결 기록(미전송 목록에 둘 것). */
fun pendingBattleRun(battleId: Long = 9) = CompletedRun(
    recordId = "rec-$battleId",
    mode = RunMode.BATTLE,
    cardKind = CardKind.BATTLE_RUN,
    battleId = battleId,
    crewRunPostId = null,
    startedAt = start,
    elapsed = Duration.ofMinutes(5),
    distance = Distance(1_000),
    route = emptyList(),
    unstable = emptyList(),
    heartRates = emptyList(),
    savedAt = start
)

fun matchFound(battleId: Long = 9) = MatchFound(
    battleId = battleId,
    opponentName = "바람처럼",
    opponentTier = "SILVER",
    distance = Distance(5_000),
    myBest = Duration.ofSeconds(1_812),
    opponentBest = Duration.ofSeconds(1_788),
    animationSeconds = 3
)

fun battleResult(pending: Boolean = false, verdict: String? = "WIN", recordId: String? = "rec-9") = BattleResult(
    battleId = 9,
    isPending = pending,
    verdict = verdict,
    rows = listOf(
        BattleResult.Row("기록", "27:30", "28:12"),
        BattleResult.Row("평균 페이스", "5:30", "5:38"),
        BattleResult.Row("가장 빠른 1km", "5:12", "공개 안 됨"),
        BattleResult.Row("추월 횟수", "2회", "1회")
    ),
    rewardPoints = 70,
    isNewRecord = false,
    opponentName = "바람처럼",
    recordId = recordId,
    myReach = java.time.Duration.ofSeconds(1_650),
    opponentReach = java.time.Duration.ofSeconds(1_692),
    type = BattleHistory.Type.RANDOM,
    totalRewardPoints = 52
)

fun history(nextCursor: Long? = null) = BattleHistory(
    entries = listOf(
        BattleHistory.Entry(
            9,
            BattleHistory.Type.RANDOM,
            "바람처럼",
            Distance(5_000),
            "WIN",
            start,
            gap = java.time.Duration.ofSeconds(-42)
        ),
        BattleHistory.Entry(
            8,
            BattleHistory.Type.SELF,
            "과거의 나",
            Distance(3_000),
            "LOSE",
            start.minusSeconds(86_400),
            gap = java.time.Duration.ofSeconds(12)
        ),
        BattleHistory.Entry(
            7,
            BattleHistory.Type.CELEBRITY,
            "번개발",
            Distance(10_000),
            null,
            start.minusSeconds(172_800),
            sourceLabel = "공개 기록 기준 · 2025 서울마라톤"
        )
    ),
    total = 12,
    wins = 7,
    losses = 5,
    selfNewRecords = 2,
    nextCursor = nextCursor
)

fun rejected(code: String) = Outcome.Failure(AppError.Rejected(code))

/** 대결 본인 기록 상세(결과 2쪽). */
fun recordDetail(recordId: String = "rec-9") = RecordDetail(
    summary = RunSummary(
        recordId = recordId,
        startedAt = start,
        distance = Distance(5_000),
        elapsed = Duration.ofSeconds(1_650),
        averagePace = null,
        isValid = true,
        mode = RunMode.BATTLE,
        cardKind = CardKind.BATTLE_RUN
    ),
    route = emptyList(),
    unstableSegments = emptyList(),
    calories = null,
    heartRateZones = emptyMap(),
    newRecords = emptyList(),
    crewContribution = null,
    rewardPoints = 12,
    weather = null
)

/** 배치 중 진행: [counted]판 셈, [provisional] 임시 티어. 센 판은 5km 승·패 번갈아. */
fun placing(counted: Int, provisional: String? = null) = PlacementProgress(
    status = PlacementStatus.IN_PROGRESS,
    countedRounds = counted,
    nextRound = counted + 1,
    provisionalTier = provisional,
    tier = "UNRANKED",
    rounds = (1..counted).map { placementRound(it, battleId = 900L + it) }
)

/** 배치고사 완료(3판 셈) 또는 면제 진행. */
fun settled(status: PlacementStatus, tier: String, verdicts: List<String> = listOf("WIN", "LOSE", "WIN")) =
    PlacementProgress(
        status = status,
        countedRounds = if (status == PlacementStatus.COMPLETED) 3 else 0,
        nextRound = null,
        provisionalTier = null,
        tier = tier,
        rounds = if (status == PlacementStatus.COMPLETED) {
            verdicts.mapIndexed { index, verdict -> placementRound(index + 1, 900L + index + 1, verdict) }
        } else {
            emptyList()
        }
    )

fun placementRound(
    round: Int,
    battleId: Long,
    verdict: String = if (round % 2 ==
        1
    ) {
        "WIN"
    } else {
        "LOSE"
    }
) = PlacementRound(
    round = round,
    battleId = battleId,
    battleDistance = Distance(5_000),
    distance = Distance(5_210 - (round - 1) * 210L),
    elapsed = Duration.ofSeconds(1_730L - round * 20),
    averagePace = com.a207.runkk.app.core.model.Pace(332 + round * 4),
    verdict = verdict
)
