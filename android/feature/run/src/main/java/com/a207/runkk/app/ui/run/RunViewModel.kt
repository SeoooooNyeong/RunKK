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
    val isConsentRequired: Boolean = false,
    /** 재동의 안내를 뒤로가기·바깥 누름으로 닫았다. 다시 시도하면 다시 보인다. */
    val isConsentDialogDismissed: Boolean = false,
    /** 누름 안내("길게 눌러 종료"·"길게 눌러 해제"). 잠깐 보였다 사라진다. */
    val hint: UiText? = null,
    /** 새 1km 지점 배너("2km 통과 · 11:04 · 평균 5:32"). */
    val splitBanner: UiText? = null,
    val error: UiText? = null
) {
    /** 목표까지 남은 거리. 목표가 없거나 넘었으면 null. */
    val remaining: Distance? get() = targetDistance?.takeIf { it > progress.distance }?.minus(progress.distance)

    /** 재동의 안내를 보일 때(시작 전, 권한·동의 없음, 닫지 않음). */
    val shouldShowConsentDialog: Boolean get() = phase == RunPhase.PREPARING && isConsentRequired &&
        !isConsentDialogDismissed

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
    private var planLoaded = false
    private var startDevice = RunStartSpec.StartDevice.PHONE
    private var hintJob: Job? = null
    private var bannerJob: Job? = null

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
        viewModelScope.launch {
            plan = records.todayPlan().getOrNull()?.let { PlanSnapshot(it.distance, it.pace) }
            planLoaded = true
            _state.update { it.copy(targetDistance = plan?.targetDistance, targetPace = plan?.targetPace) }
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

    fun onFinishLongPressed() {
        // 저장 뒤 연타가 NotSaved로 결과 화면 이동을 덮지 않게 측정·잠금 단계에서만 받는다.
        val phase = _state.value.phase
        if (phase != RunPhase.RUNNING && phase != RunPhase.LOCKED) return
        when (val outcome = tracker.onFinishLongPressed()) {
            is FinishOutcome.Saved -> {
                _state.update { it.copy(phase = RunPhase.FINISHING) }
                _events.trySend(RunEvent.Finished(outcome.run.recordId))
            }

            FinishOutcome.NotSaved -> _events.trySend(RunEvent.NotSaved)

            FinishOutcome.Rejected -> showHint(R.string.run_hold_to_unlock)
        }
    }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    private fun prepare(device: RunStartSpec.StartDevice = RunStartSpec.StartDevice.PHONE) {
        if (_state.value.phase != RunPhase.PREPARING || !planLoaded) return
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
            RunStartSpec(mode = RunMode.FREE, cardKind = CardKind.FREE_RUN, startDevice = startDevice, todayPlan = plan)
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
    }
}
