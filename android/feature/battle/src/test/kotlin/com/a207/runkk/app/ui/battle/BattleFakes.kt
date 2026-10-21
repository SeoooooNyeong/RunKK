package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import com.a207.runkk.app.domain.battle.MatchAcceptance
import com.a207.runkk.app.domain.battle.OpponentTrack
import com.a207.runkk.app.domain.battle.RecordBattleCandidates
import com.a207.runkk.app.domain.battle.RecordBattleStart
import com.a207.runkk.app.domain.live.LiveChannel
import com.a207.runkk.app.domain.live.LiveChannelProvider
import com.a207.runkk.app.domain.live.MatchFound
import com.a207.runkk.app.domain.live.ProgressReport
import com.a207.runkk.app.domain.live.ServerMessage
import com.a207.runkk.app.domain.run.CompletedRun
import com.a207.runkk.app.domain.run.Guidance
import com.a207.runkk.app.domain.run.GuidanceOutput
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.LocationPermission
import com.a207.runkk.app.domain.run.MeasurementHost
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.RunTracker
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
import kotlinx.coroutines.flow.flowOf
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
    var match: Outcome<MatchAcceptance> = Outcome.Success(MatchAcceptance(waiting = true, distance = Distance(5_000)))
    var cancelResult: Outcome<MatchAcceptance> = Outcome.Success(MatchAcceptance(waiting = false, distance = null))
    var selfResult: Outcome<RecordBattleCandidates> = Outcome.Success(RecordBattleCandidates(selfCandidates))
    var celebResult: Outcome<RecordBattleCandidates> = Outcome.Success(RecordBattleCandidates(celebCandidates))
    var startResult: Outcome<RecordBattleStart>? = null
    var resultResult: Outcome<BattleResult> = Outcome.Success(battleResult())
    var historyResult: Outcome<BattleHistory> = Outcome.Success(history())

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

    override suspend fun forfeit(battleId: Long): Outcome<Unit> {
        calls += "forfeit:$battleId"
        return Outcome.Success(Unit)
    }

    override suspend fun result(battleId: Long): Outcome<BattleResult> {
        calls += "result:$battleId"
        return resultResult
    }

    override suspend fun history(filter: BattleHistory.Type?): Outcome<BattleHistory> {
        calls += "history:${filter?.name}"
        return historyResult
    }
}

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
    var connectError: Exception? = null
    var connectFailure: AppError? = null
    private val flow = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)

    override suspend fun connect(): Outcome<Unit> {
        connects++
        connectError?.let { throw it }
        return connectFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }

    override fun send(m: ProgressReport) {
        sent += m
    }

    override val messages: Flow<ServerMessage> = flow

    override fun close() {
        closed++
    }

    fun push(m: ServerMessage) {
        check(flow.tryEmit(m))
    }
}

class FakeLocationPermission(var granted: Boolean = true) : LocationPermission {
    override fun granted() = granted
}

private class NoPendingRuns : PendingRunRepository {
    val runs = MutableStateFlow<List<CompletedRun>>(emptyList())

    override suspend fun save(run: CompletedRun): Outcome<Unit> {
        runs.value = runs.value + run
        return Outcome.Success(Unit)
    }

    override suspend fun pending(): Outcome<List<CompletedRun>> = Outcome.Success(runs.value)

    override fun observePending(): Flow<List<CompletedRun>> = runs

    override suspend fun remove(recordId: String) = Outcome.Success(Unit)

    override suspend fun purgeRoutes() = Outcome.Success(Unit)

    override suspend fun purgeHeartRates() = Outcome.Success(Unit)

    override suspend fun needsReminder(now: Instant) = Outcome.Success(false)

    override suspend fun appendHeartRates(recordId: String, samples: List<HeartRateSample>) = Outcome.Success(Unit)

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
    override val startRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val heartRates: Flow<List<HeartRateSample>> = MutableSharedFlow()

    override fun publish(d: WatchDisplay) = Unit

    override fun isConnected() = false
}

private class ConsentedSettings : SettingsRepository {
    override suspend fun refresh(): Outcome<Unit> = Outcome.Success(Unit)

    override fun locationConsented() = true

    override fun heartRateConsented() = false

    override fun watchLinked() = false

    override fun courseMapShared() = false

    override fun birthYear(): Int? = null

    override fun guidanceSettings() = GuidanceSettings.DEFAULT

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
    val tracker =
        RunTracker(NoPendingRuns(), silent, NoWatch(), silent, permission, silent, ConsentedSettings(), clock, scope)
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

val celebCandidates = listOf(
    RecordBattleCandidates.Candidate(7, "번개발", Distance(10_000), Duration.ofSeconds(2_530), null, "2025 서울마라톤", false),
    RecordBattleCandidates.Candidate(8, "마라톤왕", Distance(42_195), Duration.ofSeconds(7_800), null, "2024 춘천마라톤", true)
)

fun recordStart(distance: Distance, kind: CardKind, name: String) = RecordBattleStart(
    battleId = 42,
    opponentName = name,
    distance = distance,
    track = OpponentTrack.uniform(distance, Duration.ofSeconds(1_650)),
    cardKind = kind
)

fun matchFound(battleId: Long = 9) = MatchFound(
    battleId = battleId,
    opponentName = "바람처럼",
    opponentTier = "실버",
    distance = Distance(5_000),
    myBest = Duration.ofSeconds(1_812),
    opponentBest = Duration.ofSeconds(1_788),
    animationSeconds = 3
)

fun battleResult(pending: Boolean = false, verdict: String? = "WIN") = BattleResult(
    battleId = 9,
    pending = pending,
    verdict = verdict,
    rows = listOf(
        BattleResult.Row("기록", "27:30", "28:12"),
        BattleResult.Row("평균 페이스", "5:30", "5:38"),
        BattleResult.Row("가장 빠른 1km", "5:12", "공개 안 됨"),
        BattleResult.Row("추월 횟수", "2회", "1회")
    ),
    rewardPoints = 70,
    newRecord = false
)

fun history() = BattleHistory(
    entries = listOf(
        BattleHistory.Entry(9, BattleHistory.Type.RANDOM, "바람처럼", Distance(5_000), "WIN", start),
        BattleHistory.Entry(8, BattleHistory.Type.SELF, "과거의 나", Distance(3_000), "LOSE", start.minusSeconds(86_400)),
        BattleHistory.Entry(7, BattleHistory.Type.CELEBRITY, "번개발", Distance(10_000), null, start.minusSeconds(172_800))
    ),
    total = 12,
    wins = 7,
    losses = 5,
    selfNewRecords = 2
)

fun rejected(code: String) = Outcome.Failure(AppError.Rejected(code))
