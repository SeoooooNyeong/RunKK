package com.a207.runkk.app.ui.run

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.run.FinishOutcome
import com.a207.runkk.app.domain.run.FreeRunGoalUseCase
import com.a207.runkk.app.domain.run.PauseLimit
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.RunProgress
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.app.domain.run.RunTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 자유런 측정 화면(B1~B3, FR-030~FR-036·FR-180~FR-183). 측정 상태는 RunTracker(@Singleton)가 갖고 이 ViewModel은
 * 그 StateFlow를 구독해 화면 상태로 바꾼다(13.2 측정). 화면 입력은 누름 종류마다 다른 연산으로 넘긴다(D-36, D-49):
 * onFinishTapped·onFinishLongPressed, lockScreen·onUnlockTapped·onUnlockLongPressed.
 * 취소(onDiscardTapped → 확인 → RunTracker.discardRun)는 기록을 남기지 않고 끝내며, 끝난 결과(NotSaved)로 홈에 간다.
 * 시작은 checkStartable → StartCountdown → startRun(자유런, 목표). 워치의 시작 요청(watchStartRequests)도 같은 흐름을 탄다.
 * 목표는 카운트다운을 시작할 때마다 FreeRunGoalUseCase로 고른다(코치 계획 따르기 켬 + 오늘 계획 있음 → 오늘 계획,
 * 그 밖 → 자유런 설정 목표 거리, FR-048). 카운트다운 중 설정에서 바꾸고 돌아오면 바뀐 목표로 다시 센다.
 * 정지 경과(B2-2 "정지한 지", B2-3 자동 종료 안내)는 진행값에 정지 시작 시각이 없어 정지를 본 순간부터 [clock]으로 1초마다 센다.
 */
@HiltViewModel
class RunViewModel @Inject constructor(
    private val tracker: RunTracker,
    private val records: RecordRepository,
    private val goals: FreeRunGoalUseCase,
    private val clock: Clock
) : ViewModel() {
    private val _state = MutableStateFlow(RunUiState())
    val state: StateFlow<RunUiState> = _state.asStateFlow()

    private val _events = Channel<RunEvent>(Channel.BUFFERED)
    val events: Flow<RunEvent> = _events.receiveAsFlow()

    private val countdown = StartCountdown(viewModelScope, RunUiState.COUNTDOWN_SECONDS)

    /** 오늘 계획(서버 원본). 목표는 이것과 설정 사본으로 고른다. */
    private var todayPlan: PlanSnapshot? = null

    /** 이번 자유런의 목표(RunStartSpec.todayPlan에 싣는다). */
    private var goal: PlanSnapshot? = null
    private var planLoaded = false
    private var startDevice = RunStartSpec.StartDevice.PHONE
    private var hintJob: Job? = null
    private var bannerJob: Job? = null
    private var pauseJob: Job? = null

    /** 정지를 처음 본 시각. 정지 중이 아니면 null. */
    private var pausedSince: Instant? = null

    /** 마지막으로 배너를 띄운 km. 첫 진행값(화면을 다시 연 경우 지금 거리)으로 맞추고 배너는 띄우지 않는다. */
    private var lastSplitKm: Long? = null

    init {
        viewModelScope.launch { tracker.progress.collect(::onProgress) }
        viewModelScope.launch {
            tracker.consentRequired.collect { required -> _state.update { it.copy(isConsentRequired = required) } }
        }
        viewModelScope.launch {
            tracker.watchStartRequests.collect { prepare(RunStartSpec.StartDevice.WATCH) }
        }
        // 화면 길게 누름·워치 종료·정지 60분 자동 종료(FR-195) 모두 같은 길로 결과·홈으로 간다.
        // 구독을 먼저 걸고(UNDISPATCHED: 여기서 바로 구독) 화면이 떨어져 있던 동안 끝난 자유런이 남아 있으면(A4) 카운트다운 대신
        // 그 결과로 간다. 순서가 반대면 그 사이에 끝난 결과를 방출로도 보관으로도 받지 못한다.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { tracker.ended.collect(::onEnded) }
        tracker.takeUnclaimedEnd()?.let { if (_state.value.phase != RunPhase.FINISHING) moveToEnd(it) }
        viewModelScope.launch {
            todayPlan = records.todayPlan().getOrNull()?.let { PlanSnapshot(it.distance, it.pace) }
            planLoaded = true
            prepare()
        }
    }

    /** 재동의(권한 허용·동의 관리) 뒤 다시 시작. */
    fun onRetryStart() {
        _state.update { it.copy(isConsentDialogDismissed = false) }
        prepare()
    }

    /** 화면에 돌아왔을 때(동의 관리·설정에서 복귀) 준비 단계면 다시 확인한다. 닫은 안내는 다시 띄우지 않는다. */
    fun onResumed() = prepare()

    /**
     * 카운트다운 중 자유런 설정으로 갈 때: 카운트다운을 멈추고 준비 단계로 돌린다. 설정 화면 뒤에서 측정이 시작되지 않게 하고,
     * 돌아오면 onResumed가 바뀐 설정으로 다시 센다.
     */
    fun onSettingsTapped() {
        if (_state.value.phase != RunPhase.COUNTDOWN) return
        countdown.cancel()
        _state.update { it.copy(phase = RunPhase.PREPARING, countdown = RunUiState.COUNTDOWN_SECONDS) }
    }

    /** 재동의 안내를 뒤로가기·바깥 누름으로 닫는다. 화면은 "다시 시도"와 함께 남는다. */
    fun onConsentDialogDismissed() = _state.update { it.copy(isConsentDialogDismissed = true) }

    fun onCountdownTapped() = countdown.skip()

    fun onCancelCountdown() {
        countdown.cancel()
        _events.trySend(RunEvent.Exit)
    }

    fun onLockTapped() = tracker.lockScreen()

    fun onUnlockTapped() {
        tracker.onUnlockTapped()
        showHint(R.string.run_hold_to_unlock)
    }

    fun onUnlockLongPressed() = tracker.onUnlockLongPressed()

    fun onFinishTapped() {
        tracker.onFinishTapped()
        showHint(R.string.run_hold_to_finish)
    }

    /**
     * 길게 눌러 종료(FR-181, 정지 중에도 FR-036). 잠금 중(PAUSED_LOCKED 포함)이면 거부되어 해제를 안내한다(D-157).
     * 끝난 결과(저장됨·저장 안 함)는 RunTracker.ended로 받아 이동한다(워치·자동 종료와 같은 길).
     */
    fun onFinishLongPressed() {
        // 저장 뒤 연타가 NotSaved로 결과 화면 이동을 덮지 않게 측정·잠금 단계에서만 받는다.
        val phase = _state.value.phase
        if (phase != RunPhase.RUNNING && phase != RunPhase.LOCKED) return
        if (tracker.onFinishLongPressed() == FinishOutcome.Rejected) showHint(R.string.run_hold_to_unlock)
    }

    /** 취소(측정·정지 화면): 확인 대화상자를 띄운다. 잠금·종료 중에는 받지 않는다(FR-182). */
    fun onDiscardTapped() {
        if (_state.value.phase == RunPhase.RUNNING) _state.update { it.copy(isDiscardConfirming = true) }
    }

    /** 취소 확인에서 "계속 달리기"·뒤로가기·바깥 누름. 측정은 그대로 이어 간다. */
    fun onDiscardDismissed() = _state.update { it.copy(isDiscardConfirming = false) }

    /**
     * 취소 확인: 기록을 남기지 않고 측정을 끝낸다(서버 전송·미전송 보관 없음). 끝난 결과(NotSaved)는 RunTracker.ended로 받아
     * 길게 눌러 종료와 같은 길로 홈에 간다. 그 사이 잠겼으면 RunTracker가 거부하고 측정을 이어 간다.
     */
    fun onDiscardConfirmed() {
        val isConfirming = _state.value.isDiscardDialogVisible
        _state.update { it.copy(isDiscardConfirming = false) }
        if (isConfirming) tracker.discardRun()
    }

    /** 일시정지(자유런, FR-194). 잠금 중에는 폰 버튼을 받지 않는다(FR-182, RunTracker가 거른다). */
    fun onPauseTapped() {
        if (_state.value.phase == RunPhase.RUNNING) tracker.onPauseTapped()
    }

    /** 재개(FR-194). 잠금 중 워치 정지(PAUSED_LOCKED)는 폰 버튼으로 재개하지 않는다(D-120). */
    fun onResumeTapped() {
        if (_state.value.phase == RunPhase.RUNNING) tracker.onResumeTapped()
    }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    /** 측정이 끝났다: 저장됐으면 결과로, 거리 0이면 홈으로(FR-036). 측정 화면일 때 한 번만 옮긴다. */
    private fun onEnded(outcome: FinishOutcome) {
        val phase = _state.value.phase
        if (phase != RunPhase.RUNNING && phase != RunPhase.LOCKED) return
        moveToEnd(outcome)
    }

    private fun moveToEnd(outcome: FinishOutcome) {
        val event = when (outcome) {
            is FinishOutcome.Saved -> RunEvent.Finished(outcome.run.recordId)
            FinishOutcome.NotSaved -> RunEvent.NotSaved
            FinishOutcome.Rejected -> return
        }
        _state.update { it.copy(phase = RunPhase.FINISHING) }
        _events.trySend(event)
    }

    private fun prepare(device: RunStartSpec.StartDevice = RunStartSpec.StartDevice.PHONE) {
        if (_state.value.phase != RunPhase.PREPARING || !planLoaded) return
        val current = goals.current(todayPlan)
        val chosen = current.plan
        goal = chosen
        _state.update {
            it.copy(
                targetDistance = chosen?.targetDistance,
                targetPace = chosen?.targetPace,
                isFromCoachPlan = current.isFromCoachPlan
            )
        }
        val startable = tracker.checkStartable()
        if (startable is Outcome.Failure) {
            // 권한·동의 없음은 RunTracker가 consentRequired를 참으로 두고 화면이 재동의 안내를 보인다(design-review.md M25).
            // 그 밖의 거부는 문구로 알린다.
            val consent = (startable.error as? AppError.Rejected)?.code == AppError.Rejected.LOCATION_CONSENT
            if (!consent) _state.update { it.copy(error = RunkkErrorText.of(startable.error)) }
            return
        }
        startDevice = device
        _state.update { it.copy(phase = RunPhase.COUNTDOWN, countdown = RunUiState.COUNTDOWN_SECONDS) }
        countdown.begin(onTick = { n -> _state.update { it.copy(countdown = n) } }, onFinish = ::startRun)
    }

    private fun startRun() {
        val spec =
            RunStartSpec(mode = RunMode.FREE, cardKind = CardKind.FREE_RUN, startDevice = startDevice, todayPlan = goal)
        when (val started = tracker.startRun(spec)) {
            is Outcome.Success -> _state.update { it.copy(phase = RunPhase.RUNNING) }

            is Outcome.Failure -> {
                val code = (started.error as? AppError.Rejected)?.code
                when (code) {
                    // 이미 측정 중(화면을 다시 연 경우): 이어서 보인다.
                    AppError.Rejected.RUN_IN_PROGRESS -> _state.update {
                        it.copy(phase = if (tracker.progress.value.isLocked) RunPhase.LOCKED else RunPhase.RUNNING)
                    }

                    else -> _state.update {
                        it.copy(phase = RunPhase.PREPARING, error = RunkkErrorText.of(started.error))
                    }
                }
            }
        }
        startDevice = RunStartSpec.StartDevice.PHONE
    }

    private fun onProgress(progress: RunProgress) {
        val km = progress.distance.meters / METERS_PER_KM
        val last = lastSplitKm
        val newSplit = if (last != null && km > last) {
            uiText(
                R.string.run_split_banner,
                km,
                RunkkFormat.duration(progress.elapsed),
                RunkkFormat.pace(progress.averagePace)
            )
        } else {
            null
        }
        if (last == null || km > last) lastSplitKm = km
        if (newSplit != null) {
            bannerJob?.cancel()
            bannerJob = viewModelScope.launch {
                delay(BANNER_MILLIS)
                _state.update { it.copy(splitBanner = null) }
            }
        }
        _state.update { s ->
            val phase = when {
                s.phase == RunPhase.RUNNING && progress.isLocked -> RunPhase.LOCKED
                s.phase == RunPhase.LOCKED && !progress.isLocked -> RunPhase.RUNNING
                else -> s.phase
            }
            s.copy(progress = progress, phase = phase, splitBanner = newSplit ?: s.splitBanner)
        }
        trackPause(progress.isPaused)
    }

    /**
     * 정지로 바뀌면 그 시각부터 1초마다 정지 경과를 갱신하고, 재개·종료되면 멈춘다. 정지는 60분이면 자동 종료되므로(FR-195)
     * 그보다 조금 길게만 센다. 끝없는 반복은 가상 시간 시험(runTest 끝의 advanceUntilIdle)을 멈추지 않게 만든다.
     */
    private fun trackPause(isPaused: Boolean) {
        if (isPaused == (pausedSince != null)) return
        pauseJob?.cancel()
        if (!isPaused) {
            pausedSince = null
            _state.update { it.copy(pausedFor = null) }
            return
        }
        val since = clock.instant()
        pausedSince = since
        pauseJob = viewModelScope.launch {
            repeat(MAX_PAUSE_TICKS) {
                val pausedFor = Duration.between(since, clock.instant()).coerceAtLeast(Duration.ZERO)
                _state.update { it.copy(pausedFor = pausedFor) }
                delay(TICK_MILLIS)
            }
        }
    }

    private fun showHint(@StringRes text: Int) {
        _state.update { it.copy(hint = UiText.Res(text)) }
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(HINT_MILLIS)
            _state.update { it.copy(hint = null) }
        }
    }

    private companion object {
        const val HINT_MILLIS = 2_500L
        const val BANNER_MILLIS = 10_000L
        const val METERS_PER_KM = 1_000L
        const val TICK_MILLIS = 1_000L

        /** 정지 경과를 세는 최대 횟수: 자동 종료(60분)에 1분 여유. */
        val MAX_PAUSE_TICKS: Int = PauseLimit.END_AFTER.plusMinutes(1).seconds.toInt()
    }
}
