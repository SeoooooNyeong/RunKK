package com.a207.runkk.app.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.SelfieFailure
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
 * 실패로 끝난 작업은 기기에 남겨(lastSelfieFailure) 다시 들어와도 셀카 카드에 실패와 「다시 시도」를 보이고, 서버에 한 번 다시 물어
 * 결과가 불명확했던 작업을 서버가 회수했으면 결과를 받는다(DV-성-9, DV-성-10).
 * 셀카는 얼굴 변환 전송 동의(셀카 카드의 체크, FACE_TRANSFER)가 있어야 보낸다. 체크하면 바로 동의를 저장한다.
 * 셀카 거부(동의 없음·하루 1회 초과·재시도 한도)는 사유와 함께 기본 캐릭터 선택을 안내한다.
 */
@HiltViewModel
class AvatarSetupViewModel internal constructor(
    private val avatars: AvatarRepository,
    /** 셀카 줄이기 디스패처(리뷰 N-10). */
    private val encodeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** 상태 조회 사이 대기(밀리초). 차례로 늘리고 마지막 값을 되풀이한다. */
    private val pollDelays: List<Long> = POLL_DELAYS,
    /** 얼굴 변환 전송 동의(셀카 카드의 체크). */
    private val faceConsent: FaceConsent = GIVEN
) : ViewModel() {
    @Inject
    constructor(avatars: AvatarRepository, accounts: AccountRepository) :
        this(avatars, Dispatchers.Default, POLL_DELAYS, FaceConsent.of(accounts))

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
            val pendingJob = avatars.pendingSelfieJobId()
            if (pendingJob != null) {
                follow(pendingJob, isFirstCheckNow = true)
            } else {
                avatars.lastSelfieFailure()?.let { recheck(it) }
            }
        }
        viewModelScope.launch {
            faceConsent.isGiven()?.let { given -> _state.update { it.copy(hasFaceConsent = given) } }
        }
    }

    /** 셀카 카드의 동의 체크. 바로 서버에 동의를 저장한다. 이미 동의했으면 그대로다(철회는 마이 > 동의 관리). */
    fun onFaceConsent() {
        val s = _state.value
        if (s.hasFaceConsent || s.isSavingConsent) return
        _state.update { it.copy(isSavingConsent = true, error = null) }
        viewModelScope.launch {
            when (val result = faceConsent.give()) {
                is Outcome.Success -> _state.update { it.copy(isSavingConsent = false, hasFaceConsent = true) }

                is Outcome.Failure -> _state.update {
                    it.copy(isSavingConsent = false, error = AvatarErrorText.of(result.error))
                }
            }
        }
    }

    /** 셀카 찍기·앨범을 눌렀다. 동의하지 않았으면 체크를 먼저 하라고 알리고 거짓을 돌려준다(카메라를 열지 않는다). */
    fun onSelfieRequested(): Boolean {
        if (isBusy()) return false
        if (!_state.value.hasFaceConsent) {
            _state.update { it.copy(error = UiText.Res(R.string.avatar_setup_face_consent_needed)) }
            return false
        }
        return true
    }

    fun onCharacterSelected(character: DefaultCharacter) = _state.update { it.copy(selected = character, error = null) }

    /** 셀카 촬영을 취소했거나 사진을 읽지 못했을 때. */
    fun onSelfieUnavailable() = _state.update { it.copy(error = UiText.Res(R.string.avatar_setup_selfie_unavailable)) }

    /** 찍은 셀카를 줄여(긴 변 1024px·JPEG·1MB) 보낸다. 줄이는 동안에도 다시 누르지 못하게 먼저 isWorking을 켠다. */
    fun onSelfieCaptured(bitmap: android.graphics.Bitmap) {
        if (isBusy()) return
        _state.update { it.copy(isWorking = true, error = null, canRetrySelfie = false) }
        viewModelScope.launch {
            val jpeg = withContext(encodeDispatcher) { SelfieEncoder.encode(bitmap) }
            upload(jpeg)
        }
    }

    fun onSelfie(jpeg: ByteArray) {
        if (isBusy()) return
        _state.update { it.copy(isWorking = true, error = null, canRetrySelfie = false) }
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

    /** 남긴 실패를 보이고 서버에 한 번 다시 묻는다. 회수됐으면(SUCCEEDED) 결과를 받고, 그 밖에는 실패를 그대로 둔다. */
    private suspend fun recheck(failure: SelfieFailure) {
        failJob(failure.code)
        val job = avatars.selfieJob(failure.jobId).getOrNull() ?: return
        if (job.status != SelfieJob.Status.FAILED) {
            _state.update { it.copy(canRetrySelfie = false, error = null) }
            onJob(job)
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
                    done()
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
        it.copy(isMakingFace = false, error = AvatarErrorText.ofSelfieFailure(code), canRetrySelfie = true)
    }

    private fun fail(error: AppError) {
        val code = (error as? AppError.Rejected)?.code
        _state.update {
            it.copy(
                canCreateSelfie = it.canCreateSelfie && code !in SELFIE_BLOCKED,
                hasFaceConsent = it.hasFaceConsent && code != CONSENT_REQUIRED,
                error = if (code == CONSENT_REQUIRED) {
                    UiText.Res(R.string.avatar_setup_face_consent_needed)
                } else {
                    AvatarErrorText.ofSelfie(error)
                }
            )
        }
    }

    /**
     * 기본 캐릭터 저장. 셀카 얼굴을 만드는 중에도 저장할 수 있다(FR-158): 서버는 그 뒤 늦게 끝난 셀카 결과를 고른 기본 캐릭터 위에
     * 반영하지 않는다(Avatar.chooseDefault가 진행 중 요청을 지운다). 남은 작업은 다음에 이어 조회해 끝낸다.
     */
    fun onConfirmCharacter() {
        val character = _state.value.selected ?: return
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true, saveError = null) }
        viewModelScope.launch {
            when (val result = avatars.chooseDefault(character.name)) {
                is Outcome.Success -> {
                    pollJob?.cancel()
                    _state.update { it.copy(isWorking = false, isMakingFace = false) }
                    done()
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isWorking = false, saveError = AvatarErrorText.of(result.error))
                }
            }
        }
    }

    private var isDone = false

    /** 꾸미기로 돌아간다. 셀카 완료와 기본 캐릭터 저장이 겹쳐도 한 번만. */
    private fun done() {
        if (isDone) return
        isDone = true
        _events.trySend(AvatarSetupEvent.Done)
    }

    internal companion object {
        private const val CONSENT_REQUIRED = "FACE_CONSENT_REQUIRED"

        /** 시험 기본값: 이미 동의했다. */
        private val GIVEN = object : FaceConsent {
            override suspend fun isGiven(): Boolean = true

            override suspend fun give(): Outcome<Unit> = Outcome.Success(Unit)
        }

        /** 얼굴 생성은 보통 1~6분 걸린다. 3초부터 늘려 15초마다 본다. */
        val POLL_DELAYS = listOf(3_000L, 3_000L, 5_000L, 8_000L, 10_000L, 15_000L)

        /** 아바타(Node) 서버의 생성 제한(head-jobs generationTimeoutMs). 조회는 이보다 오래 이어 간다(DV-성-9). */
        const val AVATAR_GENERATION_LIMIT_MILLIS = 15 * 60_000L

        /** 이만큼 보고도 끝나지 않으면(약 16분, 서버 생성 제한 15분 뒤) 멈추고 다시 들어올 때 이어 본다. */
        const val MAX_POLLS = 68

        /** [MAX_POLLS]번 보는 동안 기다리는 시간. */
        fun pollWindowMillis(delays: List<Long> = POLL_DELAYS): Long =
            (0 until MAX_POLLS).sumOf { delays[it.coerceAtMost(delays.lastIndex)] }

        /** 연속 연결·서버 실패 허용 횟수. */
        const val MAX_FAILURES = 3
    }
}
