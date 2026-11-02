package com.a207.runkk.watch.battle

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import com.a207.runkk.app.domain.battle.BattleRunState
import com.a207.runkk.app.domain.battle.MatchAcceptance
import com.a207.runkk.app.domain.battle.OfflineRecordBattle
import com.a207.runkk.app.domain.battle.OpponentTrack
import com.a207.runkk.app.domain.battle.RandomBattleGate
import com.a207.runkk.app.domain.battle.RecordBattleKind
import com.a207.runkk.app.domain.run.FinishOutcome
import com.a207.runkk.app.domain.run.PendingRunRepository
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.watch.cache.OpponentRecordCache
import com.a207.runkk.watch.link.MatchWaiter
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold

/**
 * 대결 시작·취소를 domain에 넘기고 대결 상태·상대 기록·결과를 watch.ui 표시 값([BattleStep], [RecordOpponent], [WatchResultHeadline])으로
 * 바꾼다(3.5, D-386 R65). watch.ui와 domain.battle·watch.cache 사이의 중간 계층이다(design-review 23장 R75 결정, W30): watch.ui는
 * domain.battle·watch.cache를 이 클래스 너머로만 본다. 상태를 갖지 않는다: 대결 한 회의 상태는 BattleRunCoordinator가, 매칭 대기 연결은
 * watch.link의 MatchWaiter가, 상대 기록은 OpponentRecordCache가, 지금 연 기록 대결의 상대는 RecordBattleMemory(WR13-2)가 갖는다. 셀프·셀럽런을 서버 대결로 할지(연결 있음) 워치에서만 할지
 * (연결 없음)는 WatchFlowViewModel이 고른다(R72). 셀럽 상대는 고를 때 구간 기록을 받는다([readyCelebrity]·[fetchCelebrity], W31).
 * 여기서 연 대결은 모두 워치가 시작한 회라 시작 기기 WATCH로 조정자에 넘긴다(FR-245, D-378).
 */
class WatchBattleFlow @Inject constructor(
    private val coordinator: BattleRunCoordinator,
    private val battleRepository: BattleRepository,
    private val cache: OpponentRecordCache,
    private val waiter: MatchWaiter,
    private val pendingRuns: PendingRunRepository,
    private val memory: RecordBattleMemory,
    private val clock: Clock
) {
    /** 대결 한 회의 단계(BattleRunCoordinator.state를 화면 값으로 옮김). */
    val steps: Flow<BattleStep> get() = coordinator.state.map(::stepOf)

    /** 지금 대결 단계. 화면을 다시 열 때 맥락을 되살린다(WR12-2). */
    fun step(): BattleStep = stepOf(coordinator.state.value)

    /**
     * 랜덤 매칭을 연다(FR-257): 대기 채널을 먼저 연결하고 매칭을 요청한다. 연결하지 못하거나 요청이 거부되면 Failure이고 대기 채널을 닫는다.
     * 받아들여지면 [awaitMatch]로 성사를 기다린다.
     */
    suspend fun startMatching(distance: Distance): Outcome<MatchAcceptance> {
        memory.clear()
        val opened = waiter.open()
        if (opened is Outcome.Failure) return opened
        val accepted = coordinator.requestMatch(distance)
        if (accepted is Outcome.Failure) waiter.close()
        return accepted
    }

    /**
     * 매칭 대기의 끝을 기다린다. 성사되면 조정자에 넘기고(onMatched) 대기 채널의 출발 예약을 잇는다(대기 임대는 [releaseWait]까지 쥔다).
     * 서버가 끝내면(MatchFailed) 대기 채널을 닫고 그 사유 코드로 Failure(Rejected)다(서버가 이미 대기열에서 뺐다). 끊기면 서버 대기를
     * 정리하고 끊긴 사유(모르면 Network)로 Failure다. 화면은 사유 코드로 안내를 고른다(코드 리뷰 WR12-1).
     */
    suspend fun awaitMatch(): Outcome<Unit> = when (val end = waiter.awaitEnd()) {
        is MatchWaiter.End.Found -> {
            coordinator.onMatched(end.match, RunStartSpec.StartDevice.WATCH)
            waiter.forwardStarts(end.match.battleId, coordinator::onStart)
            Outcome.Success(Unit)
        }

        is MatchWaiter.End.Failed -> {
            waiter.close()
            Outcome.Failure(AppError.Rejected(end.code))
        }

        is MatchWaiter.End.Lost -> {
            waiter.close()
            coordinator.abandonMatching()
            Outcome.Failure(end.error ?: AppError.Network)
        }
    }

    /** 매칭 대기 취소(FR-085): 대기 채널을 닫고 서버 대기열에서 뺀다(조정자가 앱 범위에서 보낸다). */
    fun cancelMatching() {
        waiter.close()
        coordinator.abandonMatching()
    }

    /** 성사 뒤 쥐고 있던 대기 임대를 반납한다(측정이 시작됐거나 대결이 끝났다). */
    fun releaseWait() = waiter.close()

    /** 서버 기록 대결(연결 있음, FR-258·FR-259): POST /battles/self·celebrity → 3초 카운트다운 뒤 측정(onRecordBattleStart). */
    suspend fun startRecordOnline(opponent: RecordOpponent): Outcome<Unit> {
        memory.clear()
        val record = opponent.cached.record
        val started = when (record.kind) {
            RecordBattleKind.SELF -> battleRepository.startSelfBattle(record.distance)

            RecordBattleKind.CELEBRITY -> record.celebrityId?.let { battleRepository.startCelebrityBattle(it) }
                ?: return Outcome.Failure(AppError.Rejected(NO_CELEBRITY))
        }
        return when (started) {
            is Outcome.Success -> {
                memory.remember(opponent)
                coordinator.onRecordBattleStart(started.value, RunStartSpec.StartDevice.WATCH)
                Outcome.Success(Unit)
            }

            is Outcome.Failure -> started
        }
    }

    /** 워치에서만 하는 기록 대결(연결 없음, D-377 U3 (나)): 미리 받은 상대 기록으로 진행하고 기록은 자유런으로 올린다. */
    fun startRecordOffline(opponent: RecordOpponent) {
        val record = opponent.cached.record
        memory.remember(opponent)
        coordinator.onOfflineRecordBattle(
            OfflineRecordBattle(record.kind, record.distance, record.celebrityId, record.track)
        )
    }

    /** 대결 도착 전 끝내기(FR-248): 배틀런 포기, 셀프·셀럽런 그만두기. 기록은 남는다(FR-094). */
    fun forfeit() = coordinator.forfeit()

    /**
     * 기본 대결 거리(FR-109, GET /battles/match): 마지막으로 매칭을 요청한 거리이고 처음이면 서버 기본값이다. 매칭 대기 중이면 그 거리다.
     * 읽지 못하면 null이다.
     */
    suspend fun defaultDistance(): Distance? = when (val status = battleRepository.matchStatus()) {
        is Outcome.Success -> status.value.run { distance?.takeIf { isWaiting } ?: defaultDistance }
        is Outcome.Failure -> null
    }

    /** 열린 대결의 거리(BattleRunCoordinator.activeDistance). 성사~출발 사이에 화면을 다시 열 때 쓴다(WR12-2). */
    fun activeDistance(): Distance? = coordinator.activeDistance

    /**
     * 열린 회(카운트다운·측정·종료 카운트다운)가 이 워치에서 연 셀프·셀럽 기록 대결이면 그 상대 기록, 배틀런이거나 열린 회가 없으면
     * null(코드 리뷰 WR13-2). 측정 중이나 카운트다운 중에 화면을 다시 열 때 셀프·셀럽 맥락을 되살린다. 끝난 회를 화면이 확인하기 전에
     * 사라져 기억이 남아 있어도 끝 상태(Finished·Discarded)에서는 돌려주지 않는다.
     */
    fun activeRecord(): RecordOpponent? {
        if (!step().isOpen) memory.clear()
        return memory.current()
    }

    /** 기록 대결 기억을 지운다(WR14-2): 회가 끝났거나 다른 회·모드가 열렸거나 회원이 바뀌었다. */
    fun forgetRecord() = memory.clear()

    /** 결과 화면이 대결 끝(Finished·Discarded)을 받았다. 다음 대결이 지난 끝 상태를 받지 않게 한다. */
    fun acknowledge() {
        memory.clear()
        coordinator.acknowledgeFinished()
    }

    /** 배틀런 결과 머리말(FR-249): 서버 판정과 도착 때 상대와의 거리 차([gapMeters]). 판정을 받지 못하면 보류다. */
    suspend fun battleHeadline(battleId: Long, gapMeters: Int?): WatchResultHeadline =
        WatchResultHeadline.forBattle(result(battleId), gapMeters)

    /** 서버 대결로 달린 셀프·셀럽런의 결과 머리말(서버 판정, W27, FR-250). 판정을 받지 못하면 보류다. */
    suspend fun recordVerdictHeadline(opponent: RecordOpponent, battleId: Long): WatchResultHeadline {
        val result = result(battleId)
        return when (opponent.cached.record.kind) {
            RecordBattleKind.SELF -> WatchResultHeadline.forSelfVerdict(result)
            RecordBattleKind.CELEBRITY -> WatchResultHeadline.forCelebVerdict(result, opponent.displayName)
        }
    }

    /**
     * 워치 계산 머리말(FR-250): 상대 완주 시간과 내 도착 시간을 화면에 보이는 초 단위로 견준다(같은 초는 진 것, W28). 도착 전에 끝냈으면
     * STOP이다. 셀프런을 새로 앞섰으면 상대 기록 사본도 바꾼다(FR-260).
     */
    fun recordWatchHeadline(opponent: RecordOpponent, outcome: FinishOutcome): WatchResultHeadline {
        val record = opponent.cached.record
        val run = (outcome as? FinishOutcome.Saved)?.run
        val gapSeconds = run?.takeIf { it.distance.isAtLeast(record.distance) }
            ?.let { record.finishTime.seconds - it.elapsed.seconds }
        return when (record.kind) {
            RecordBattleKind.CELEBRITY -> WatchResultHeadline.forCeleb(gapSeconds, record.displayName)

            RecordBattleKind.SELF -> WatchResultHeadline.forSelf(gapSeconds).also { headline ->
                if (headline is WatchResultHeadline.Best && run != null) onNewSelfBest(record.distance, run.elapsed)
            }
        }
    }

    /** 랜덤 대결까지 남은 유효 러닝 수(폰 RandomBattleGate와 같은 규칙, FR-080). null이면 열림 또는 모름(서버가 판정한다). */
    fun randomRunsLeft(tier: String?, validRuns: Int?): Int? = RandomBattleGate.runsLeft(tier, validRuns)

    fun selfCandidates(): List<RecordOpponent> = cache.selfCandidates().map(::RecordOpponent)

    fun celebrityCandidates(d: Distance): List<RecordOpponent> = cache.celebrityCandidates(d).map(::RecordOpponent)

    /**
     * 셀럽 상대를 구간 기록까지 받아 두었으면 그 상대(W31). 목록 화면이 가진 [c]는 목록을 열 때의 값이라 구간 기록이 없을 수 있어
     * 셀럽 ID로 캐시를 다시 본다. 받아 두지 않았으면 null이다.
     */
    fun readyCelebrity(c: RecordOpponent): RecordOpponent? =
        c.cached.record.celebrityId?.let(cache::readyCelebrity)?.let(::RecordOpponent)

    /**
     * 셀럽 상대의 구간 기록을 받아 캐시에 두고 그 상대를 돌려준다(W31). 실패는 사유 그대로다: 연결 없음 Network, 없는 기록(404)
     * Rejected. 셀럽 ID가 없는 기록은 받지 않고 Rejected다.
     */
    suspend fun fetchCelebrity(c: RecordOpponent): Outcome<RecordOpponent> {
        val celebrityId = c.cached.record.celebrityId ?: return Outcome.Failure(AppError.Rejected(NO_CELEBRITY))
        return when (val fetched = cache.fetchCelebrityTrack(celebrityId)) {
            is Outcome.Success -> Outcome.Success(RecordOpponent(fetched.value))
            is Outcome.Failure -> fetched
        }
    }

    /** 상대 기록을 다시 받는다(메인을 열 때와 기록 전송 뒤, FR-260). 회원ID는 부르는 쪽이 넘긴다(M78). */
    suspend fun refreshCache(memberId: Long) {
        cache.refresh(memberId)
    }

    /** 워치 셀프런이 최고 기록을 새로 세웠다(FR-260 제안). 구간 기록이 없어 내 도착 시간의 균등 페이스로 둔다. */
    fun onNewSelfBest(distance: Distance, finish: Duration) {
        cache.onNewSelfBest(distance, OpponentTrack.uniform(distance, finish))
    }

    /** 미전송 기록이 줄 때마다(전송됨) 한 번씩 낸다. 상대 기록을 다시 받는 신호다(SQ-46). */
    fun uploadsSent(): Flow<Unit> = pendingRuns.observePending()
        .map { it.size }
        .runningFold(Int.MAX_VALUE to Int.MAX_VALUE) { (_, previous), size -> previous to size }
        .filter { (previous, size) -> previous != Int.MAX_VALUE && size < previous }
        .map { }

    /** [startAt]까지 남은 초(올림, 0 이상). 배틀런 출발 카운트다운이 쓴다(FR-087). */
    fun secondsUntil(startAt: Instant): Long {
        val millis = Duration.between(clock.instant(), startAt).toMillis()
        return if (millis <= 0) 0 else (millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
    }

    /** 배틀런·기록 대결 서버 결과. 받지 못하면 null(보류로 보인다). */
    private suspend fun result(battleId: Long): BattleResult? =
        (battleRepository.result(battleId) as? Outcome.Success)?.value

    internal companion object {
        private const val MILLIS_PER_SECOND = 1_000L

        /** 셀럽 ID 없는 셀럽 기록(받은 값 이상). */
        private const val NO_CELEBRITY = "NO_CELEBRITY"

        /** 대결 상태 → 화면이 쓰는 단계. 내 측정값·상대 연결 같은 폰 화면용 값은 버린다. */
        fun stepOf(state: BattleRunState): BattleStep = when (state) {
            BattleRunState.Idle -> BattleStep.Idle

            is BattleRunState.Matched -> BattleStep.Matched(
                battleId = state.match.battleId,
                distance = state.match.distance,
                opponentName = state.match.opponentName,
                opponentGender = state.match.opponentGender
            )

            is BattleRunState.Countdown -> BattleStep.Countdown(state.battleId, state.startAt)

            is BattleRunState.Running -> BattleStep.Running(state.gapMeters, state.lastGuidance)

            is BattleRunState.Closing -> BattleStep.Closing

            is BattleRunState.Finished -> BattleStep.Finished(state.battleId)

            is BattleRunState.Discarded -> BattleStep.Discarded
        }
    }
}
