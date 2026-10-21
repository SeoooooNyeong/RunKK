package com.a207.runkk.app.ui.run

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.run.FinishOutcome
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.RunProgress
import com.a207.runkk.app.domain.run.RunStartSpec
import com.a207.runkk.app.domain.run.RunTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

/** 러닝 화면 단계: 준비(재동의 대기) → 카운트다운 → 측정 ⇄ 잠금 → 종료 중. */
enum class RunPhase { PREPARING, COUNTDOWN, RUNNING, LOCKED, FINISHING }

data class RunUiState(
    val phase: RunPhase = RunPhase.PREPARING,
    val countdown: Int = COUNTDOWN_SECONDS,
    val progress: RunProgress = RunProgress.INITIAL,
    val targetDistance: Distance? = null,
    val targetPace: Pace? = null,
    val consentRequired: Boolean = false,
    /** 재동의 안내를 뒤로가기·바깥 누름으로 닫았다. 다시 시도하면 다시 보인다. */
    val consentDialogDismissed: Boolean = false,
    /** 누름 안내("길게 눌러 종료"·"길게 눌러 해제"). 잠깐 보였다 사라진다. */
    val hint: String? = null,
    /** 새 1km 지점 배너("2km 통과 · 11:04 · 평균 5:32"). */
    val splitBanner: String? = null,
    val error: String? = null
) {
    /** 목표까지 남은 거리. 목표가 없거나 넘었으면 null. */
    val remaining: Distance? get() = targetDistance?.takeIf { it > progress.distance }?.minus(progress.distance)

    /** 재동의 안내를 보일 때(시작 전, 권한·동의 없음, 닫지 않음). */
    val showConsentDialog: Boolean get() = phase == RunPhase.PREPARING && consentRequired && !consentDialogDismissed

    val targetProgress: Float? get() = targetDistance?.takeIf { it.meters > 0 }?.let {
        (
            progress.distance.meters.toFloat() /
                it.meters
            ).coerceIn(0f, 1f)
    }

    companion object {
        const val COUNTDOWN_SECONDS = 3
    }
}

sealed interface RunEvent {
    /** 저장됨: 결과 화면으로. */
    data class Finished(val recordId: String) : RunEvent

    /** 거리 0이라 저장하지 않음: 홈으로. */
    data object NotSaved : RunEvent

    /** 카운트다운 취소: 이전 화면으로. */
    data object Exit : RunEvent
}

/**
 * 자유런 측정 화면(B1~B3, FR-030~FR-036·FR-180~FR-183). 측정 상태는 RunTracker(@Singleton)가 갖고 이 ViewModel은
 * 그 StateFlow를 구독해 화면 상태로 바꾼다(13.2 측정). 화면 입력은 누름 종류마다 다른 연산으로 넘긴다(D-36, D-49):
 * onFinishTapped·onFinishLongPressed, lockScreen·onUnlockTapped·onUnlockLongPressed.
 * 시작은 checkStartable → StartCountdown → startRun(자유런, 오늘 계획). 워치의 시작 요청(watchStartRequests)도 같은 흐름을 탄다.
 */
@HiltViewModel
class RunViewModel @Inject constructor(private val tracker: RunTracker, private val records: RecordRepository) :
    ViewModel() {
    private val _state = MutableStateFlow(RunUiState())
    val state: StateFlow<RunUiState> = _state.asStateFlow()

    private val _events = Channel<RunEvent>(Channel.BUFFERED)
    val events: Flow<RunEvent> = _events.receiveAsFlow()

    private val countdown = StartCountdown(viewModelScope, RunUiState.COUNTDOWN_SECONDS)
    private var plan: PlanSnapshot? = null
    private var startDevice = RunStartSpec.StartDevice.PHONE
    private var hintJob: Job? = null
    private var lastSplitKm = 0L

    init {
        viewModelScope.launch { tracker.progress.collect(::onProgress) }
        viewModelScope.launch {
            tracker.consentRequired.collect { required -> _state.update { it.copy(consentRequired = required) } }
        }
        viewModelScope.launch {
            tracker.watchStartRequests.collect {
                startDevice = RunStartSpec.StartDevice.WATCH
                prepare()
            }
        }
        viewModelScope.launch {
            plan = records.todayPlan().getOrNull()?.let { PlanSnapshot(it.distance, it.pace) }
            _state.update { it.copy(targetDistance = plan?.targetDistance, targetPace = plan?.targetPace) }
            prepare()
        }
    }

    /** 재동의(권한 허용·동의 관리) 뒤 다시 시작. */
    fun onRetryStart() {
        _state.update { it.copy(consentDialogDismissed = false) }
        prepare()
    }

    /** 재동의 안내를 뒤로가기·바깥 누름으로 닫는다. 화면은 "다시 시도"와 함께 남는다. */
    fun onConsentDialogDismissed() = _state.update { it.copy(consentDialogDismissed = true) }

    fun onCountdownTapped() = countdown.skip()

    fun onCancelCountdown() {
        countdown.cancel()
        _events.trySend(RunEvent.Exit)
    }

    fun onLockTapped() = tracker.lockScreen()

    fun onUnlockTapped() {
        tracker.onUnlockTapped()
        showHint(UNLOCK_HINT)
    }

    fun onUnlockLongPressed() = tracker.onUnlockLongPressed()

    fun onFinishTapped() {
        tracker.onFinishTapped()
        showHint(FINISH_HINT)
    }

    fun onFinishLongPressed() {
        when (val outcome = tracker.onFinishLongPressed()) {
            is FinishOutcome.Saved -> {
                _state.update { it.copy(phase = RunPhase.FINISHING) }
                _events.trySend(RunEvent.Finished(outcome.run.recordId))
            }

            FinishOutcome.NotSaved -> _events.trySend(RunEvent.NotSaved)

            FinishOutcome.Rejected -> showHint(UNLOCK_HINT)
        }
    }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    private fun prepare() {
        if (_state.value.phase != RunPhase.PREPARING) return
        // 권한·동의가 없으면 RunTracker가 consentRequired를 참으로 두고 화면은 재동의 안내를 보인다(design-review.md M25).
        if (tracker.checkStartable() is Outcome.Failure) return
        _state.update { it.copy(phase = RunPhase.COUNTDOWN, countdown = RunUiState.COUNTDOWN_SECONDS) }
        countdown.begin(onTick = { n -> _state.update { it.copy(countdown = n) } }, onFinish = ::startRun)
    }

    private fun startRun() {
        val spec =
            RunStartSpec(mode = RunMode.FREE, cardKind = CardKind.FREE_RUN, startDevice = startDevice, todayPlan = plan)
        when (val started = tracker.startRun(spec)) {
            is Outcome.Success -> _state.update { it.copy(phase = RunPhase.RUNNING) }

            is Outcome.Failure -> {
                val code = (started.error as? AppError.Rejected)?.code
                when (code) {
                    // 이미 측정 중(화면을 다시 연 경우): 이어서 보인다.
                    AppError.Rejected.RUN_IN_PROGRESS -> _state.update { it.copy(phase = RunPhase.RUNNING) }

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
        val newSplit = if (km > lastSplitKm) {
            lastSplitKm = km
            "${km}km 통과 · ${RunkkFormat.duration(progress.elapsed)} · 평균 ${RunkkFormat.pace(progress.averagePace)}"
        } else {
            null
        }
        _state.update { s ->
            val phase = when {
                s.phase == RunPhase.RUNNING && progress.locked -> RunPhase.LOCKED
                s.phase == RunPhase.LOCKED && !progress.locked -> RunPhase.RUNNING
                else -> s.phase
            }
            s.copy(progress = progress, phase = phase, splitBanner = newSplit ?: s.splitBanner)
        }
    }

    private fun showHint(text: String) {
        _state.update { it.copy(hint = text) }
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            delay(HINT_MILLIS)
            _state.update { it.copy(hint = null) }
        }
    }

    private companion object {
        const val FINISH_HINT = "길게 눌러 종료"
        const val UNLOCK_HINT = "길게 눌러 해제"
        const val HINT_MILLIS = 2_500L
        const val METERS_PER_KM = 1_000L
    }
}
