package com.a207.runkk.watch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.battle.BattleRunState
import com.a207.runkk.app.domain.battle.RecordBattleKind
import com.a207.runkk.app.domain.run.FinishOutcome
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.RunProgress
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.app.domain.run.RunTracker
import com.a207.runkk.watch.cache.CachedTrack
import com.a207.runkk.watch.guide.WatchGuidanceOutput
import com.a207.runkk.watch.link.NetworkState
import com.a207.runkk.watch.link.PhoneLink
import com.a207.runkk.watch.session.WatchSession
import com.a207.runkk.watch.session.WatchSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 워치 화면 흐름 상태(WatchFlow, 상태도 2.4.25)를 정하고 사용자 조작을 domain에 넘긴다(3.5, D-374). 협력 객체는 일곱이다(D-386):
 * 측정 RunTracker, 대결 WatchBattleFlow, 로그인 WatchSessionStore, 폰 PhoneLink(동행 표시·로그인 정보 요청), 서버 연결 NetworkState,
 * 안내 문구 WatchGuidanceOutput, 본인 요약 AccountRepository(티어·성별).
 *
 * - 시작 전 확인: 폰이 측정 중이면 거부(FR-252), 위치 동의가 없으면 "폰에서 위치 수집 동의를 해 주세요"(FR-245). 배틀런은 서버에 닿지
 *   않으면 시작하지 않는다(FR-251). 셀프·셀럽런은 서버에 닿으면 서버 대결, 아니면 미리 받은 상대 기록으로 워치에서만 한다(D-377, R72).
 * - 자유런은 이 ViewModel이 3·2·1·GO 뒤 RunTracker.startRun을 부르고, 대결은 BattleRunCoordinator가 출발을 정한다.
 * - 끝: 자유런은 RunTracker.ended, 대결은 BattleRunState.Finished로 결과(WatchResultHeadline)를 만든다. 측정 서비스가 밖에서 끝내면
 *   (운동 세션 실패) 기록이 있으면 결과, 없으면 "측정이 중단됐어요"로 메인에 돌아간다.
 * - 로그인: 로그인 정보가 없으면 폰에 요청하고(SESSION_REQUEST) 미로그인 안내를 보인다(FR-242). 결과를 닫을 때 로그아웃 대기였으면
 *   지운다(completeRevoke). 메인을 열 때 서버 설정·본인 요약·상대 기록을 맞춘다(D-379, FR-260).
 */
@HiltViewModel
class WatchFlowViewModel @Inject constructor(
    private val runTracker: RunTracker,
    private val battleFlow: WatchBattleFlow,
    private val sessionStore: WatchSessionStore,
    private val phoneLink: PhoneLink,
    private val connectivity: NetworkState,
    private val guidance: WatchGuidanceOutput,
    private val accounts: AccountRepository
) : ViewModel() {
    /** 러닝 한 회의 화면 맥락. [opponent]는 셀프·셀럽 상대 기록, [battleId]는 서버 대결 ID(오프라인 기록 대결은 0)다. */
    private data class Round(
        val mode: WatchMode,
        val goal: Distance?,
        val opponent: CachedTrack? = null,
        val battleId: Long? = null,
        val hasStarted: Boolean = false,
        val isEndedByUser: Boolean = false
    )

    private var round: Round? = null
    private var lastMode = WatchMode.FREE
    private var myTier: String? = null
    private var isPhoneRunning = false
    private var lastGap: Int? = null
    private var lastLead: Any? = null

    /** 이 회의 종료 요약. 대결 결과는 Finished와 이 값이 모두 와야 만든다(오는 순서는 정해져 있지 않다). */
    private val endedOutcome = MutableStateFlow<FinishOutcome?>(null)

    private var countdownJob: Job? = null
    private var matchingJob: Job? = null
    private var waitingJob: Job? = null
    private var resultJob: Job? = null
    private var leadJob: Job? = null
    private var noticeJob: Job? = null

    private val _flow = MutableStateFlow(initialFlow())
    val flow: StateFlow<WatchFlow> = _flow.asStateFlow()

    init {
        viewModelScope.launch { sessionStore.state.collect(::onSession) }
        viewModelScope.launch { accounts.myGender.collect { gender -> updateMain { it.copy(gender = gender) } } }
        viewModelScope.launch {
            phoneLink.displays.map { it.isRunning }.distinctUntilChanged().collect(::onPhoneRunning)
        }
        viewModelScope.launch {
            connectivity.isOnline.collect { online ->
                _flow.value.let { if (it is WatchFlow.BattleDistance) _flow.value = it.copy(isOnline = online) }
            }
        }
        viewModelScope.launch {
            combine(runTracker.progress, guidance.banner) { progress, banner -> progress to banner?.text }
                .collect { (progress, banner) -> onProgress(progress, banner) }
        }
        viewModelScope.launch { runTracker.ended.collect(::onEnded) }
        viewModelScope.launch { battleFlow.state.collect(::onBattleState) }
        // 기록을 보낸 뒤 상대 기록을 다시 받는다(SQ-46). 회원이 바뀌면 이전 구독을 끊는다.
        viewModelScope.launch {
            sessionStore.state.collectLatest { session ->
                if (session is WatchSession.Active) {
                    battleFlow.uploadsSent().collect { battleFlow.refreshCache(session.memberId) }
                }
            }
        }
    }

    /** 메인 가운데 카드를 눌렀다(FR-243). 그 모드의 거리 선택으로 간다. */
    fun onModeSelected(mode: WatchMode) {
        if (_flow.value !is WatchFlow.Main) return
        lastMode = mode
        _flow.value = pickerOf(mode)
    }

    /**
     * 거리 카드(FR-244, FR-257~FR-259). 자유런·배틀런은 가운데 카드를 바꾸고(시작은 [onStartPressed]), 셀프런은 그 거리 기록으로
     * 바로 시작하며(기록 없는 거리는 무시, W10), 셀럽런은 그 거리의 셀럽 목록으로 간다. 자유런 무제한은 null이다.
     */
    fun onDistanceSelected(d: Distance?) {
        when (val current = _flow.value) {
            is WatchFlow.FreeGoal -> current.indexOf(d)?.let {
                _flow.value = current.copy(selected = it, notice = null)
            }

            is WatchFlow.BattleDistance -> current.indexOf(d)?.let {
                _flow.value =
                    current.copy(selected = it, notice = null)
            }

            is WatchFlow.SelfDistance -> {
                val track = d?.let { distance ->
                    battleFlow.selfCandidates().firstOrNull {
                        it.record.distance ==
                            distance
                    }
                }
                if (track != null) startRecord(track, WatchMode.SELF)
            }

            is WatchFlow.CelebDistance -> {
                val celebrities = d?.let(battleFlow::celebrityCandidates).orEmpty()
                if (d != null && celebrities.isNotEmpty()) _flow.value = WatchFlow.CelebList(d, celebrities)
            }

            else -> Unit
        }
    }

    /** 셀럽 기록 하나를 골랐다(FR-259). 카운트다운으로 간다. */
    fun onCelebritySelected(c: CachedTrack) {
        if (_flow.value !is WatchFlow.CelebList) return
        startRecord(c, WatchMode.CELEB)
    }

    /** "달리기 시작"(자유런, FR-244)·"매칭 시작"(배틀런, FR-257). */
    fun onStartPressed() {
        when (val current = _flow.value) {
            is WatchFlow.FreeGoal -> startFree(current)
            is WatchFlow.BattleDistance -> startMatching(current)
            else -> Unit
        }
    }

    /** 매칭 대기 취소(FR-085). 거리 선택으로 돌아간다. */
    fun onCancelMatching() {
        val matching = _flow.value as? WatchFlow.Matching ?: return
        matchingJob?.cancel()
        waitingJob?.cancel()
        battleFlow.cancelMatching()
        round = null
        _flow.value = battlePicker(selectedDistance = matching.distance)
    }

    /** 자유런 일시정지(FR-247). 대결에는 일시정지가 없다(W-21, I14-12). */
    fun onPause() {
        val running = _flow.value as? WatchFlow.Running ?: return
        if (running.isPausable) runTracker.onPauseTapped()
    }

    /** 일시정지 화면의 "계속". */
    fun onResume() {
        if (_flow.value is WatchFlow.Paused) runTracker.onResumeTapped()
    }

    /** 종료를 짧게 눌렀다(FR-181, FR-248). 끝내지 않고 "길게 눌러 종료"를 잠깐 보인다. */
    fun onEndTapped() = showNotice(WatchFlow.Notice.HOLD_TO_END)

    /** 종료를 길게 눌렀다(FR-248). 자유런은 기록을 확정하고, 대결은 포기·그만두기로 끝낸다(기록은 남는다, FR-094). */
    fun onEndLongPressed() {
        val current = _flow.value
        if (current !is WatchFlow.Running && current !is WatchFlow.Paused) return
        val active = round ?: return
        round = active.copy(isEndedByUser = true)
        if (active.mode == WatchMode.FREE) runTracker.onFinishLongPressed() else battleFlow.forfeit()
    }

    /** 결과의 "완료"(FR-249). 메인(또는 로그아웃 대기였으면 미로그인 안내)으로 간다. */
    fun onResultClosed() {
        if (_flow.value !is WatchFlow.Result) return
        resultJob?.cancel()
        battleFlow.acknowledge()
        round = null
        if (sessionStore.state.value is WatchSession.RevokePending) {
            _flow.value = WatchFlow.NotLoggedIn
            viewModelScope.launch { sessionStore.completeRevoke() }
            return
        }
        enterHome(refresh = true)
    }

    /** 뒤로 가기(SwipeToDismissBox·뒤로 버튼). 측정·대결 중(카운트다운·측정·일시정지·매칭 완료)에는 받지 않는다(2.4.25). */
    fun back() {
        when (val current = _flow.value) {
            is WatchFlow.Picker -> enterHome(refresh = false)
            is WatchFlow.CelebList -> _flow.value = celebPicker(selectedDistance = current.distance)
            is WatchFlow.Matching -> onCancelMatching()
            else -> Unit
        }
    }

    /** 워치 OS 위치 권한 창에서 거절했다(FR-245). 고른 화면에 안내를 띄운다. */
    fun onLocationPermissionDenied() = showNotice(WatchFlow.Notice.LOCATION_PERMISSION)

    override fun onCleared() {
        // 성사 전에 화면이 사라지면 서버 대기열에서 뺀다(FR-085). 성사 뒤 대결은 조정자가 갖는다.
        if (_flow.value is WatchFlow.Matching) battleFlow.cancelMatching()
    }

    private fun initialFlow(): WatchFlow {
        if (runTracker.isMeasuring) {
            // 워치가 측정 중에 앱을 다시 열었다(2.4.25 [*] → Running). 화면 맥락은 진행 알림 값과 대결 상태로 되살린다.
            val isFree = battleFlow.state.value is BattleRunState.Idle
            val goal = runTracker.notice.value?.progressEnd
            val restored = Round(if (isFree) WatchMode.FREE else WatchMode.BATTLE, goal, hasStarted = true)
            round = restored
            val progress = runTracker.progress.value
            return if (isFree && progress.isPaused) {
                WatchFlow.Paused(progress, goal)
            } else {
                WatchFlow.Running(restored.mode, progress, goal, isPausable = isFree)
            }
        }
        return if (sessionStore.state.value is WatchSession.None) WatchFlow.NotLoggedIn else WatchFlow.Main()
    }

    private fun onSession(session: WatchSession) {
        when (session) {
            WatchSession.None -> {
                if (isIdle(_flow.value) && _flow.value !is WatchFlow.Companion) _flow.value = WatchFlow.NotLoggedIn
                viewModelScope.launch { phoneLink.requestSession() }
            }

            is WatchSession.Active -> {
                when (_flow.value) {
                    WatchFlow.NotLoggedIn -> enterHome(refresh = true)
                    is WatchFlow.Main -> refreshAccount(session.memberId)
                    else -> Unit
                }
            }

            is WatchSession.RevokePending -> if (!runTracker.isMeasuring && _flow.value !is WatchFlow.Result) {
                // 측정 중 받은 로그아웃을 결과를 닫지 못한 채 앱이 다시 떴다: 지금 지운다.
                viewModelScope.launch { sessionStore.completeRevoke() }
            }
        }
    }

    private fun onPhoneRunning(isRunning: Boolean) {
        isPhoneRunning = isRunning
        val current = _flow.value
        when {
            isRunning && !runTracker.isMeasuring && round == null && isIdle(current) ->
                _flow.value =
                    WatchFlow.Companion

            !isRunning && current is WatchFlow.Companion -> enterHome(refresh = false)
        }
    }

    /** 메인(로그인 정보가 없으면 미로그인 안내)으로 간다. [refresh]면 서버 설정·본인 요약·상대 기록을 맞춘다. */
    private fun enterHome(refresh: Boolean, notice: WatchFlow.Notice? = null) {
        val session = sessionStore.state.value
        if (isPhoneRunning && !runTracker.isMeasuring) {
            _flow.value = WatchFlow.Companion
            return
        }
        if (session !is WatchSession.Active) {
            _flow.value = WatchFlow.NotLoggedIn
            return
        }
        _flow.value = WatchFlow.Main(lastMode, accounts.myGender.value, notice)
        if (refresh) refreshAccount(session.memberId)
    }

    private fun refreshAccount(memberId: Long) {
        viewModelScope.launch {
            sessionStore.refreshSettings()
            (accounts.mySummary() as? Outcome.Success)?.value?.tier?.let { myTier = it }
            battleFlow.refreshCache(memberId)
        }
    }

    private fun startFree(picker: WatchFlow.FreeGoal) {
        blockedNotice()?.let { notice ->
            _flow.value = picker.copy(notice = notice)
            return
        }
        val goal = picker.current.distance
        beginRound(Round(WatchMode.FREE, goal))
        _flow.value = WatchFlow.Countdown(WatchMode.FREE, goal, COUNTDOWN_SECONDS)
        countdownJob = viewModelScope.launch {
            for (left in COUNTDOWN_SECONDS downTo 1) {
                _flow.value = WatchFlow.Countdown(WatchMode.FREE, goal, left)
                delay(SECOND_MILLIS)
            }
            val spec = RunStartSpec(
                mode = RunMode.FREE,
                cardKind = CardKind.FREE_RUN,
                startDevice = RunStartSpec.StartDevice.WATCH,
                todayPlan = goal?.let { PlanSnapshot(targetDistance = it, targetPace = null) }
            )
            val started = runTracker.startRun(spec)
            if (started is Outcome.Failure) {
                round = null
                _flow.value = picker.copy(notice = noticeOf(started.error))
                return@launch
            }
            round = round?.copy(hasStarted = true)
            _flow.value = WatchFlow.Countdown(WatchMode.FREE, goal, 0)
            delay(GO_MILLIS)
            showRunning()
        }
    }

    private fun startMatching(picker: WatchFlow.BattleDistance) {
        val notice = if (!connectivity.isOnline.value) WatchFlow.Notice.BATTLE_OFFLINE else blockedNotice()
        if (notice != null) {
            _flow.value = picker.copy(notice = notice)
            return
        }
        val distance = picker.current.distance ?: return
        beginRound(Round(WatchMode.BATTLE, distance))
        _flow.value = WatchFlow.Matching(myTier, distance)
        waitingJob = viewModelScope.launch {
            while (true) {
                delay(SECOND_MILLIS)
                (_flow.value as? WatchFlow.Matching)?.let {
                    _flow.value = it.copy(waitedSeconds = it.waitedSeconds + 1)
                }
            }
        }
        matchingJob = viewModelScope.launch {
            val failure = when (val accepted = battleFlow.startMatching(distance)) {
                is Outcome.Failure -> noticeOf(accepted.error, fallback = WatchFlow.Notice.MATCH_FAILED)
                is Outcome.Success -> if (battleFlow.awaitMatch()) null else WatchFlow.Notice.MATCH_FAILED
            }
            if (failure != null && _flow.value is WatchFlow.Matching) {
                waitingJob?.cancel()
                round = null
                _flow.value = battlePicker(selectedDistance = distance).copy(notice = failure)
            }
        }
    }

    /** 셀프·셀럽런 시작: 서버에 닿으면 서버 대결, 아니면(또는 서버에 닿지 못하면) 워치에서만 한다(D-377, R72). */
    private fun startRecord(track: CachedTrack, mode: WatchMode) {
        blockedNotice()?.let { notice ->
            showNotice(notice)
            return
        }
        val picker = _flow.value
        val distance = track.record.distance
        beginRound(Round(mode, distance, opponent = track))
        _flow.value = WatchFlow.Countdown(mode, distance, COUNTDOWN_SECONDS)
        if (!connectivity.isOnline.value) {
            battleFlow.startRecordOffline(track)
            return
        }
        viewModelScope.launch {
            val started = battleFlow.startRecordOnline(track)
            if (started !is Outcome.Failure) return@launch
            if (started.error == AppError.Network) {
                battleFlow.startRecordOffline(track)
            } else {
                round = null
                _flow.value = withNotice(picker, noticeOf(started.error))
            }
        }
    }

    private fun beginRound(next: Round) {
        round = next
        endedOutcome.value = null
        lastGap = null
        lastLead = null
        resultJob?.cancel()
    }

    /** 시작을 막는 사유: 폰 측정 중(FR-252), 위치 동의·권한 없음(FR-245). 없으면 null. */
    private fun blockedNotice(): WatchFlow.Notice? = when {
        isPhoneRunning -> WatchFlow.Notice.PHONE_RUNNING
        runTracker.checkStartable() is Outcome.Failure -> WatchFlow.Notice.LOCATION_CONSENT
        else -> null
    }

    private fun onProgress(progress: RunProgress, banner: String?) {
        when (val current = _flow.value) {
            is WatchFlow.Running -> _flow.value = if (current.isPausable && progress.isPaused) {
                WatchFlow.Paused(progress, current.goal)
            } else {
                current.copy(progress = progress, banner = banner)
            }

            is WatchFlow.Paused -> _flow.value = if (progress.isPaused) {
                current.copy(progress = progress)
            } else {
                WatchFlow.Running(WatchMode.FREE, progress, current.goal, banner = banner, isPausable = true)
            }

            else -> Unit
        }
    }

    private fun onBattleState(state: BattleRunState) {
        val active = round ?: return
        if (active.mode == WatchMode.FREE) return
        when (state) {
            is BattleRunState.Matched -> {
                waitingJob?.cancel()
                round = active.copy(battleId = state.match.battleId)
                (battleFlow.toFlow(state) as? WatchFlow.Matched)?.let {
                    _flow.value = it.copy(tier = myTier, myGender = accounts.myGender.value)
                }
            }

            is BattleRunState.Countdown -> {
                round = active.copy(battleId = state.battleId)
                countdownTo(active.mode, active.goal, state.startAt)
            }

            is BattleRunState.Running -> onBattleRunning(active, state.gapMeters, state.lastGuidance)

            is BattleRunState.Closing -> onBattleRunning(active, lastGap, lastLead)

            is BattleRunState.Finished -> onBattleFinished(state.battleId)

            is BattleRunState.Discarded -> {
                battleFlow.acknowledge()
                round = null
                enterHome(refresh = false)
            }

            BattleRunState.Idle -> Unit
        }
    }

    /** 대결 출발 3·2·1(FR-087): 출발 시각까지 3초가 남을 때부터 센다. 측정 시작(Running)이 오면 GO 뒤 측정 화면이다. */
    private fun countdownTo(mode: WatchMode, goal: Distance?, startAt: Instant) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val left = battleFlow.secondsUntil(startAt)
                if (left in 1..COUNTDOWN_SECONDS.toLong()) _flow.value = WatchFlow.Countdown(mode, goal, left.toInt())
                if (left <= 0L) break
                delay(COUNTDOWN_POLL_MILLIS)
            }
        }
    }

    private fun onBattleRunning(active: Round, gap: Int?, lead: Any?) {
        if (!active.hasStarted) {
            round = active.copy(hasStarted = true)
            battleFlow.releaseWait()
            countdownJob?.cancel()
            countdownJob = viewModelScope.launch {
                _flow.value = WatchFlow.Countdown(active.mode, active.goal, 0)
                delay(GO_MILLIS)
                showRunning()
            }
        }
        lastGap = gap ?: lastGap
        val isLeadChanged = lead != null && lead != lastLead
        lastLead = lead ?: lastLead
        if (isLeadChanged) {
            leadJob?.cancel()
            leadJob = viewModelScope.launch {
                delay(LEAD_BANNER_MILLIS)
                (_flow.value as? WatchFlow.Running)?.let { _flow.value = it.copy(isLeadChanged = false) }
            }
        }
        (_flow.value as? WatchFlow.Running)?.let {
            _flow.value = it.copy(gapMeters = lastGap, isLeadChanged = it.isLeadChanged || isLeadChanged)
        }
    }

    /** 카운트다운 뒤 측정 화면. 지금 진행값과 이 회의 맥락으로 만든다. */
    private fun showRunning() {
        val active = round ?: return
        val current = _flow.value
        if (current !is WatchFlow.Countdown) return
        _flow.value = WatchFlow.Running(
            mode = active.mode,
            progress = runTracker.progress.value,
            goal = active.goal,
            gapMeters = lastGap,
            banner = guidance.banner.value?.text,
            isPausable = active.mode == WatchMode.FREE
        )
    }

    private fun onEnded(outcome: FinishOutcome) {
        endedOutcome.value = outcome
        val active = round ?: return
        if (active.mode != WatchMode.FREE) {
            // 측정 서비스가 밖에서 끝냈다(운동 세션 실패): 대결 회도 닫는다(조정자는 측정 끝을 따로 알지 못한다).
            if (!active.isEndedByUser && battleFlow.state.value.isOpen()) battleFlow.forfeit()
            return
        }
        countdownJob?.cancel()
        round = null
        when {
            outcome is FinishOutcome.Saved ->
                _flow.value =
                    WatchFlow.Result(WatchResultHeadline.forFree(outcome, active.goal), RunSummaryView.of(outcome))

            active.isEndedByUser -> enterHome(refresh = false)

            else -> enterHome(refresh = false, notice = WatchFlow.Notice.MEASUREMENT_STOPPED)
        }
    }

    private fun onBattleFinished(battleId: Long) {
        val active = round ?: return
        countdownJob?.cancel()
        battleFlow.releaseWait()
        if (!active.hasStarted) {
            // 출발 전에 끝났다(연결 실패·시작 거부·상대 이탈): 결과가 없으니 거리 선택으로 돌아간다.
            battleFlow.acknowledge()
            round = null
            val picker = if (active.mode == WatchMode.BATTLE) battlePicker(active.goal) else pickerOf(active.mode)
            _flow.value = withNotice(picker, WatchFlow.Notice.START_FAILED)
            return
        }
        resultJob = viewModelScope.launch {
            val outcome = endedOutcome.value
                ?: withTimeoutOrNull(OUTCOME_WAIT_MILLIS) { endedOutcome.filterNotNull().first() }
                ?: FinishOutcome.NotSaved
            val summary = RunSummaryView.of(outcome)
            when (active.mode) {
                WatchMode.BATTLE -> showBattleResult(active.battleId ?: battleId, outcome, summary)
                else -> showRecordResult(active, outcome, summary)
            }
        }
    }

    /** 배틀런 결과: 서버 판정을 받는다. 보류면 "결과를 확인하고 있어요"를 보이고 판정이 올 때까지 다시 묻는다(FR-108). */
    private suspend fun showBattleResult(battleId: Long, outcome: FinishOutcome, summary: RunSummaryView) {
        if (battleId == OFFLINE_BATTLE_ID) {
            // 앱을 다시 열어 맥락을 잃은 오프라인 기록 대결: 상대를 몰라 자유런 결과로 보인다.
            _flow.value = WatchFlow.Result(WatchResultHeadline.forFree(outcome, goal = null), summary)
            return
        }
        _flow.value = WatchFlow.Result(WatchResultHeadline.Pending, summary)
        repeat(RESULT_ATTEMPTS) { attempt ->
            val headline = WatchResultHeadline.forBattle(battleFlow.result(battleId), lastGap)
            if (_flow.value !is WatchFlow.Result) return
            _flow.value = WatchFlow.Result(headline, summary)
            if (headline != WatchResultHeadline.Pending) return
            if (attempt < RESULT_ATTEMPTS - 1) delay(RESULT_RETRY_MILLIS)
        }
    }

    /** 셀프·셀럽런 결과: 상대 완주 시간과 내 도착 시간 차로 워치가 정한다(오프라인은 워치에서만 보인다, W18). */
    private fun showRecordResult(active: Round, outcome: FinishOutcome, summary: RunSummaryView) {
        val opponent = active.opponent?.record
        if (opponent == null) {
            _flow.value = WatchFlow.Result(WatchResultHeadline.forFree(outcome, goal = null), summary)
            return
        }
        val run = (outcome as? FinishOutcome.Saved)?.run
        val gapSeconds = run?.takeIf { it.distance.isAtLeast(opponent.distance) }
            ?.let { opponent.finishTime.minus(it.elapsed).seconds }
        val headline = WatchResultHeadline.forRecord(opponent.kind, gapSeconds, opponent.displayName)
        if (opponent.kind == RecordBattleKind.SELF && headline is WatchResultHeadline.Best && run != null) {
            battleFlow.onNewSelfBest(opponent.distance, run.elapsed)
        }
        _flow.value = WatchFlow.Result(headline, summary)
    }

    private fun showNotice(notice: WatchFlow.Notice) {
        val current = _flow.value
        val noticed = withNotice(current, notice)
        if (noticed === current) return
        _flow.value = noticed
        if (current is WatchFlow.Running || current is WatchFlow.Paused) {
            noticeJob?.cancel()
            noticeJob = viewModelScope.launch {
                delay(NOTICE_MILLIS)
                _flow.value = withNotice(_flow.value, notice = null)
            }
        }
    }

    private fun updateMain(change: (WatchFlow.Main) -> WatchFlow.Main) {
        (_flow.value as? WatchFlow.Main)?.let { _flow.value = change(it) }
    }

    private fun pickerOf(mode: WatchMode): WatchFlow = when (mode) {
        WatchMode.FREE -> WatchFlow.FreeGoal(FREE_GOALS.map { WatchFlow.Choice(it) })

        WatchMode.BATTLE -> battlePicker(selectedDistance = null)

        WatchMode.SELF -> {
            val records = battleFlow.selfCandidates()
            val choices = SELF_DISTANCES.map { distance ->
                val record = records.firstOrNull { it.record.distance == distance }?.record?.finishTime
                WatchFlow.Choice(distance, record = record, isEnabled = record != null)
            }
            WatchFlow.SelfDistance(choices, selected = firstEnabled(choices))
        }

        WatchMode.CELEB -> celebPicker(selectedDistance = null)
    }

    private fun battlePicker(selectedDistance: Distance?): WatchFlow.BattleDistance {
        val choices = BATTLE_DISTANCES.map { WatchFlow.Choice(it) }
        val selected = choices.indexOfFirst { it.distance == selectedDistance }.coerceAtLeast(0)
        return WatchFlow.BattleDistance(choices, selected, connectivity.isOnline.value)
    }

    private fun celebPicker(selectedDistance: Distance?): WatchFlow.CelebDistance {
        val choices = CELEB_DISTANCES.map { distance ->
            val count = battleFlow.celebrityCandidates(distance).size
            WatchFlow.Choice(distance, count = count, isEnabled = count > 0)
        }
        val selected =
            choices.indexOfFirst { it.distance == selectedDistance }.takeIf { it >= 0 } ?: firstEnabled(choices)
        return WatchFlow.CelebDistance(choices, selected)
    }

    internal companion object {
        val FREE_GOALS: List<Distance?> = listOf(null, Distance(3_000), Distance(5_000), Distance(10_000))
        val BATTLE_DISTANCES = listOf(Distance(1_000), Distance(3_000), Distance(5_000), Distance(10_000))
        val SELF_DISTANCES = listOf(
            Distance(1_000),
            Distance(3_000),
            Distance(5_000),
            Distance(10_000),
            Distance(HALF_MARATHON_METERS)
        )
        val CELEB_DISTANCES =
            listOf(Distance(5_000), Distance(10_000), Distance(HALF_MARATHON_METERS), Distance(FULL_MARATHON_METERS))

        const val COUNTDOWN_SECONDS = 3
        const val SECOND_MILLIS = 1_000L
        const val GO_MILLIS = 700L
        const val COUNTDOWN_POLL_MILLIS = 200L

        /** "역전!"을 보이는 시간(FR-254 안내 문구와 같은 2초). */
        const val LEAD_BANNER_MILLIS = 2_000L

        /** 측정 중 안내("길게 눌러 종료" 등)를 보이는 시간. */
        const val NOTICE_MILLIS = 2_000L

        /** 대결 끝(Finished) 뒤 종료 요약을 기다리는 상한. */
        const val OUTCOME_WAIT_MILLIS = 2_000L
        const val RESULT_ATTEMPTS = 10
        const val RESULT_RETRY_MILLIS = 3_000L
        const val OFFLINE_BATTLE_ID = 0L
        const val HALF_MARATHON_METERS = 21_097L
        const val FULL_MARATHON_METERS = 42_195L

        private fun WatchFlow.Picker.indexOf(d: Distance?): Int? = choices.indexOfFirst { it.distance == d }.takeIf {
            it >=
                0
        }

        private fun firstEnabled(choices: List<WatchFlow.Choice>): Int = choices.indexOfFirst {
            it.isEnabled
        }.coerceAtLeast(0)

        /** 거리 선택·카운트다운 전 화면(동행 표시로 바꿔도 되는 화면). */
        private fun isIdle(flow: WatchFlow): Boolean = flow is WatchFlow.NotLoggedIn || flow is WatchFlow.Main ||
            flow is WatchFlow.Picker || flow is WatchFlow.CelebList || flow is WatchFlow.Companion

        private fun BattleRunState.isOpen(): Boolean =
            this is BattleRunState.Running || this is BattleRunState.Closing ||
                this is BattleRunState.Countdown

        /** 시작 거부 사유 → 안내. 위치 동의 거부(기기·서버 사유 코드)는 동의 안내, 그 밖은 [fallback]. */
        private fun noticeOf(
            error: AppError,
            fallback: WatchFlow.Notice = WatchFlow.Notice.START_FAILED
        ): WatchFlow.Notice {
            val code = (error as? AppError.Rejected)?.code
            return when (code) {
                AppError.Rejected.LOCATION_CONSENT, LOCATION_CONSENT_REQUIRED -> WatchFlow.Notice.LOCATION_CONSENT
                else -> fallback
            }
        }

        /** 서버 매칭 거부 사유 코드(위치 동의 없음, FR-080). */
        private const val LOCATION_CONSENT_REQUIRED = "LOCATION_CONSENT_REQUIRED"

        /** [flow]에 안내를 단 사본. 안내를 둘 수 없는 화면이면 그대로. */
        private fun withNotice(flow: WatchFlow, notice: WatchFlow.Notice?): WatchFlow = when (flow) {
            is WatchFlow.Main -> flow.copy(notice = notice)
            is WatchFlow.FreeGoal -> flow.copy(notice = notice)
            is WatchFlow.BattleDistance -> flow.copy(notice = notice)
            is WatchFlow.SelfDistance -> flow.copy(notice = notice)
            is WatchFlow.CelebDistance -> flow.copy(notice = notice)
            is WatchFlow.CelebList -> flow.copy(notice = notice)
            is WatchFlow.Running -> flow.copy(notice = notice)
            is WatchFlow.Paused -> flow.copy(notice = notice)
            else -> flow
        }
    }
}
