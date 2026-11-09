package com.a207.runkk.app.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.SelfieJob
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

/**
 * 아바타 만들기(FR-155~FR-158): 셀카(앱이 줄인 JPEG)로 얼굴을 만들거나 기본 캐릭터 4종 중 하나를 chooseDefault로 고른다.
 *
 * 셀카 얼굴은 서버가 비동기로 만든다(S15P31A207-276, 248): 접수(202, 작업 ID) → 만드는 중 → 상태 조회(간격을 늘려 가며) →
 * 완료면 결과를 받아 서버가 내 얼굴을 셀카 머리(head:)로 바꾸고 꾸미기로 돌아간다. 실패면 사유 문구를 보인다.
 * 화면을 떠나도 서버 작업은 계속되므로, 다시 들어오거나 앱을 다시 켜면 남긴 작업(AvatarRepository.pendingSelfieJobId)을 이어 조회한다.
 * 셀카 거부(동의 없음·하루 1회 초과·재시도 한도)는 사유와 함께 기본 캐릭터 선택을 안내한다.
 */
@HiltViewModel
class AvatarSetupViewModel internal constructor(
    private val avatars: AvatarRepository,
    /** 셀카 줄이기 디스패처(리뷰 N-10). */
    private val encodeDispatcher: CoroutineDispatcher,
    /** 상태 조회 사이 대기(밀리초). 차례로 늘리고 마지막 값을 되풀이한다. */
    private val pollDelays: List<Long> = POLL_DELAYS
) : ViewModel() {
    @Inject
    constructor(avatars: AvatarRepository) : this(avatars, Dispatchers.Default)

    private val _state = MutableStateFlow(AvatarSetupUiState())
    val state: StateFlow<AvatarSetupUiState> = _state.asStateFlow()

    private val _events = Channel<AvatarSetupEvent>(Channel.BUFFERED)
    val events: Flow<AvatarSetupEvent> = _events.receiveAsFlow()

    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            val avatar = avatars.avatar().getOrNull()
            _state.update {
                it.copy(
                    isLoading = false,
                    canCreateSelfie = avatar?.canCreateSelfieToday ?: true,
                    faceKey = avatar?.renderKey,
                    selected = it.selected ?: DefaultCharacter.of(avatar?.defaultCharacterId)
                )
            }
        }
        viewModelScope.launch {
            avatars.pendingSelfieJobId()?.let { jobId -> follow(jobId, isFirstCheckNow = true) }
        }
    }

    fun onCharacterSelected(character: DefaultCharacter) = _state.update { it.copy(selected = character, error = null) }

    /** 셀카 촬영을 취소했거나 사진을 읽지 못했을 때. */
    fun onSelfieUnavailable() = _state.update { it.copy(error = UiText.Res(R.string.avatar_setup_selfie_unavailable)) }

    /** 찍은 셀카를 줄여(긴 변 1024px·JPEG·1MB) 보낸다. 줄이는 동안에도 다시 누르지 못하게 먼저 isWorking을 켠다. */
    fun onSelfieCaptured(bitmap: android.graphics.Bitmap) {
        if (isBusy()) return
        _state.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            val jpeg = withContext(encodeDispatcher) { SelfieEncoder.encode(bitmap) }
            upload(jpeg)
        }
    }

    fun onSelfie(jpeg: ByteArray) {
        if (isBusy()) return
        _state.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch { upload(jpeg) }
    }

    private fun isBusy(): Boolean = _state.value.isWorking || _state.value.isMakingFace

    /** 접수한 뒤에는 사진 바이트를 들고 있지 않는다(조회는 작업 ID로만 한다). */
    private suspend fun upload(jpeg: ByteArray) {
        val result = avatars.submitSelfie(jpeg)
        _state.update { it.copy(isWorking = false) }
        when (result) {
            is Outcome.Success -> onJob(result.value)
            is Outcome.Failure -> fail(result.error)
        }
    }

    /** 작업 상태에 따라 끝내거나 이어 조회한다. */
    private fun onJob(job: SelfieJob) {
        when (job.status) {
            SelfieJob.Status.SUCCEEDED -> finish(job.jobId)
            SelfieJob.Status.FAILED -> failJob(job.failureCode)
            SelfieJob.Status.QUEUED, SelfieJob.Status.RUNNING -> follow(job.jobId, isFirstCheckNow = false)
        }
    }

    /**
     * 만드는 중을 켜고 끝날 때까지 상태를 조회한다. 잠깐의 연결·서버 실패는 [MAX_FAILURES]번까지 이어 가고, 그보다 오래 끌면 멈추고
     * 다시 들어올 때 이어 조회한다(작업 ID는 남아 있다).
     */
    private fun follow(jobId: String, isFirstCheckNow: Boolean) {
        pollJob?.cancel()
        _state.update { it.copy(isMakingFace = true, error = null) }
        pollJob = viewModelScope.launch {
            var failures = 0
            for (attempt in 0 until MAX_POLLS) {
                if (attempt > 0 || !isFirstCheckNow) delay(pollDelays[attempt.coerceAtMost(pollDelays.lastIndex)])
                when (val result = avatars.selfieJob(jobId)) {
                    is Outcome.Success -> {
                        failures = 0
                        if (result.value.isOver) {
                            onJob(result.value)
                            return@launch
                        }
                    }

                    is Outcome.Failure -> {
                        if (result.error is AppError.Rejected || ++failures >= MAX_FAILURES) {
                            stopMaking(result.error)
                            return@launch
                        }
                    }
                }
            }
            _state.update {
                it.copy(isMakingFace = false, error = UiText.Res(R.string.avatar_error_selfie_in_progress))
            }
        }
    }

    private val SelfieJob.isOver: Boolean
        get() = status == SelfieJob.Status.SUCCEEDED || status == SelfieJob.Status.FAILED

    /** 결과를 받아 서버가 내 얼굴을 바꾸게 하고 꾸미기로 돌아간다. */
    private fun finish(jobId: String) {
        _state.update { it.copy(isMakingFace = true, error = null) }
        viewModelScope.launch {
            when (val result = avatars.finishSelfie(jobId)) {
                is Outcome.Success -> {
                    _state.update {
                        it.copy(
                            isMakingFace = false,
                            faceKey = result.value.faceKey,
                            canCreateSelfie = result.value.canCreateSelfieToday
                        )
                    }
                    _events.trySend(AvatarSetupEvent.Done)
                }

                is Outcome.Failure -> stopMaking(result.error)
            }
        }
    }

    private fun stopMaking(error: AppError) {
        _state.update { it.copy(isMakingFace = false) }
        fail(error)
    }

    /** 서버가 얼굴을 만들지 못했다(작업 FAILED). 모르는 사유는 공용 "얼굴을 만들지 못했어요"다. */
    private fun failJob(code: String?) = _state.update {
        it.copy(isMakingFace = false, error = AvatarErrorText.ofSelfieFailure(code))
    }

    private fun fail(error: AppError) {
        val code = (error as? AppError.Rejected)?.code
        _state.update {
            it.copy(
                canCreateSelfie = it.canCreateSelfie && code !in SELFIE_BLOCKED,
                error = AvatarErrorText.ofSelfie(error)
            )
        }
    }

    fun onConfirmCharacter() {
        val character = _state.value.selected ?: return
        if (isBusy()) return
        _state.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            when (val result = avatars.chooseDefault(character.name)) {
                is Outcome.Success -> {
                    _state.update { it.copy(isWorking = false) }
                    _events.trySend(AvatarSetupEvent.Done)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isWorking = false, error = AvatarErrorText.of(result.error))
                }
            }
        }
    }

    internal companion object {
        /** 얼굴 생성은 1~2분 걸린다. 3초부터 늘려 15초마다 본다. */
        val POLL_DELAYS = listOf(3_000L, 3_000L, 5_000L, 8_000L, 10_000L, 15_000L)

        /** 이만큼 보고도 끝나지 않으면(약 10분) 멈추고 다시 들어올 때 이어 본다. */
        const val MAX_POLLS = 42

        /** 연속 연결·서버 실패 허용 횟수. */
        const val MAX_FAILURES = 3
    }
}
