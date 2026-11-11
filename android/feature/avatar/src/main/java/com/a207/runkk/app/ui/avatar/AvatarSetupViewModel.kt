package com.a207.runkk.app.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.avatar.AvatarRepository
import com.a207.runkk.app.domain.avatar.PendingSelfiePhoto
import com.a207.runkk.app.domain.avatar.SelfieJob
import com.a207.runkk.app.domain.avatar.SelfieJobFollower
import com.a207.runkk.app.domain.avatar.SelfieProgress
import com.a207.runkk.app.domain.avatar.SelfieScreenPresence
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 얼굴 만들기(FR-155~FR-158, 피그마 AV-7 1안, design-oo 05-30 6절): 셀카·앨범 사진을 줄여(SelfieEncoder) 사진 확인 화면에 크게 보이고,
 * 동의 뒤 「만들기 시작」으로 보낸다. 접수되면 보낸 사진을 기기에 남기고([PendingSelfiePhoto]) 따라가기를 워커에 맡긴다
 * ([SelfieJobFollower], [임시:D-성-117]). 화면은 조회하지 않고 [AvatarRepository.selfieProgress]를 단계로 바꾼다: 만드는 중이면
 * 「나중에 다시 확인해도 돼요」와 보낸 사진, 끝나면 새 얼굴, 실패면 사유. 화면을 떠나도 워커가 이어 가고 결과를 알림으로 알린다.
 * 화면이 보이는 동안은 [SelfieScreenPresence]를 열어 둔다(알림 대신 화면이 바뀐다, [임시:D-성-120]).
 * 「기본 얼굴」 칸 하나를 고르면 chooseDefault로 기본 캐릭터 RABBIT을 저장한다(만드는 중에도 된다, FR-158: 서버가 진행 중 요청을 지운다).
 * 서버 기본 캐릭터 값은 어느 것이든 기본 얼굴로 본다([임시:D-성-187]).
 */
@HiltViewModel
class AvatarSetupViewModel internal constructor(
    private val avatars: AvatarRepository,
    private val follower: SelfieJobFollower,
    private val pendingPhoto: PendingSelfiePhoto,
    private val presence: SelfieScreenPresence,
    private val heads: SelfieHeads,
    /** 셀카 줄이기 디스패처(리뷰 N-10). */
    private val encodeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** 얼굴 변환 전송 동의(사진 확인 화면의 체크). */
    private val faceConsent: FaceConsent = GIVEN
) : ViewModel() {
    @Inject
    constructor(
        avatars: AvatarRepository,
        accounts: AccountRepository,
        follower: SelfieJobFollower,
        pendingPhoto: PendingSelfiePhoto,
        presence: SelfieScreenPresence,
        heads: SelfieHeads
    ) : this(avatars, follower, pendingPhoto, presence, heads, Dispatchers.Default, FaceConsent.of(accounts))

    private val _state = MutableStateFlow(AvatarSetupUiState())
    val state: StateFlow<AvatarSetupUiState> = _state.asStateFlow()

    private val _events = Channel<AvatarSetupEvent>(Channel.BUFFERED)
    val events: Flow<AvatarSetupEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch { loadAvatar() }
        viewModelScope.launch {
            faceConsent.isGiven()?.let { given -> _state.update { it.copy(hasFaceConsent = given) } }
        }
        viewModelScope.launch {
            avatars.selfieProgress().collect(::onProgress)
        }
        viewModelScope.launch {
            // 기기에 남긴 작업이 없으면 서버의 진행 중 작업을 찾아 이어 따라간다(앱 데이터를 지웠거나 앞 판에서 남긴 작업).
            avatars.pendingSelfieJobId()?.let(follower::resume)
        }
    }

    private suspend fun loadAvatar() {
        val avatar = avatars.avatar().getOrNull()
        // 지금 얼굴이 셀카면 지금 얼굴 카드가 그 머리를 그리게 부위 그림을 읽는다(못 읽으면 기본 머리).
        val layers = avatar?.takeIf { it.isSelfieFace }?.let { heads.look(it.faceKey).faceLayers }
        _state.update {
            it.copy(
                isLoading = false,
                canCreateSelfie = avatar?.canCreateSelfieToday ?: it.canCreateSelfie,
                faceKey = avatar?.renderKey ?: it.faceKey,
                faceLayers = layers ?: it.faceLayers,
                isDefaultSelected = it.isDefaultSelected || avatar?.isDefaultFace == true
            )
        }
    }

    /** 새 얼굴 화면 무대: 바뀐 얼굴과 지금 착용·체형·러닝량으로 그린다. 못 읽으면 무대는 로딩 표시만 둔다. */
    private suspend fun loadLook() {
        val avatar = avatars.avatar().getOrNull() ?: return
        val items = avatars.shop(null).getOrNull()?.items.orEmpty()
        val runAmount = avatars.runner().getOrNull()?.runAmount ?: 0
        val face = heads.look(avatar.faceKey)
        _state.update {
            it.copy(
                backgroundKey = avatar.backgroundKey,
                faceLayers = face.faceLayers,
                look = AvatarLook().withAvatar(avatar, items, runAmount).copy(headLayers = face.headLayers)
            )
        }
    }

    /** 워커가 고친 진행을 화면 단계로 바꾼다(design-oo 05-30 6.2). 고르는 중(사진 확인·보내는 중)에는 단계를 바꾸지 않는다. */
    private suspend fun onProgress(progress: SelfieProgress) {
        val phase = _state.value.phase
        if (phase == SetupPhase.CONFIRM) return
        when (progress) {
            is SelfieProgress.Working -> {
                if (phase == SetupPhase.SENDING) return
                val photo = _state.value.photo ?: pendingPhoto.load()
                _state.update { it.copy(phase = SetupPhase.WORKING, photo = photo, error = null) }
            }

            is SelfieProgress.Succeeded -> {
                // 새 셀카가 지금 얼굴이 됐으니 「기본 얼굴」 칸 고름을 푼다(그대로 두면 저장이 새 셀카를 덮는다).
                _state.update {
                    it.copy(
                        phase = SetupPhase.DONE,
                        faceKey = progress.faceKey ?: it.faceKey,
                        isDefaultSelected = false,
                        error = null
                    )
                }
                loadAvatar()
                loadLook()
            }

            is SelfieProgress.Failed -> _state.update {
                it.copy(
                    phase = SetupPhase.FAILED,
                    failure = AvatarErrorText.ofSelfieFailure(progress.code),
                    error = null
                )
            }

            SelfieProgress.None -> if (phase == SetupPhase.WORKING) {
                // 서버에 진행 중 작업이 없다고 확인돼 기기 작업을 지웠다(회수).
                _state.update { it.copy(phase = SetupPhase.CHOOSE, photo = null) }
            }
        }
    }

    /** 화면이 보인다(ON_START). 보이는 동안 결과는 알림 대신 화면으로 알린다. */
    fun onScreenVisible() = presence.opened()

    /** 화면이 안 보인다(ON_STOP). */
    fun onScreenHidden() = presence.closed()

    /** 사진 확인 화면의 동의 체크. 바로 서버에 동의를 저장한다. 이미 동의했으면 그대로다(철회는 마이 > 동의 관리). */
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

    /** 셀카 찍기·앨범을 눌렀다. 만드는 중이거나 오늘 이미 만들었으면 거짓을 돌려준다(카메라를 열지 않는다). */
    fun onSelfieRequested(): Boolean {
        val s = _state.value
        if (s.isWorking || s.isMakingFace) return false
        if (!s.canCreateSelfie) {
            _state.update { it.copy(error = UiText.Res(R.string.avatar_error_selfie_limit)) }
            return false
        }
        return true
    }

    /** 「기본 얼굴」 칸을 눌렀다([임시:D-성-187]). */
    fun onDefaultFaceSelected() = _state.update { it.copy(isDefaultSelected = true, error = null) }

    /** 찍거나 고른 사진을 읽지 못했을 때. */
    fun onSelfieUnavailable() = _state.update { it.copy(error = UiText.Res(R.string.avatar_setup_selfie_unavailable)) }

    /** 카메라를 사용자가 취소했을 때: 문구 없이 고르기 단계에 남는다(앞 안내도 지운다, 리뷰 RC-3). */
    fun onSelfieCancelled() = _state.update { it.copy(phase = SetupPhase.CHOOSE, error = null) }

    /** 찍거나 고른 사진을 줄여(긴 변 1024px·JPEG·1MB) 사진 확인 화면에 보인다. 아직 보내지 않는다. */
    fun onSelfieCaptured(bitmap: android.graphics.Bitmap) {
        if (_state.value.isWorking || _state.value.isMakingFace) return
        _state.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            val jpeg = withContext(encodeDispatcher) { SelfieEncoder.encode(bitmap) }
            onSelfie(jpeg)
        }
    }

    /** 줄인 사진을 사진 확인 화면에 보인다. */
    fun onSelfie(jpeg: ByteArray) {
        if (_state.value.isMakingFace) return
        _state.update { it.copy(isWorking = false, phase = SetupPhase.CONFIRM, photo = jpeg, error = null) }
    }

    /** 사진 확인의 뒤로·다시 고르기: 고르기로 돌아간다(사진은 버린다). */
    fun onRepick() = _state.update { it.copy(phase = SetupPhase.CHOOSE, photo = null, error = null) }

    /** 「만들기 시작」: 동의가 있어야 보낸다. 접수되면 보낸 사진을 남기고 따라가기를 워커에 맡긴다. */
    fun onStart() {
        val s = _state.value
        val jpeg = s.photo ?: return
        if (s.phase != SetupPhase.CONFIRM) return
        if (!s.hasFaceConsent) {
            _state.update { it.copy(error = UiText.Res(R.string.avatar_setup_face_consent_needed)) }
            return
        }
        _state.update { it.copy(phase = SetupPhase.SENDING, error = null) }
        viewModelScope.launch {
            when (val result = avatars.submitSelfie(jpeg)) {
                is Outcome.Success -> onSubmitted(result.value, jpeg)
                is Outcome.Failure -> fail(result.error)
            }
        }
    }

    private suspend fun onSubmitted(job: SelfieJob, jpeg: ByteArray) {
        if (job.status == SelfieJob.Status.FAILED) {
            _state.update {
                it.copy(phase = SetupPhase.FAILED, failure = AvatarErrorText.ofSelfieFailure(job.failureCode))
            }
            return
        }
        // 접수된 뒤에는 화면을 바로 떠나도(ViewModel 취소) 사진을 남기고 따라가기를 맡긴다(리뷰 R2-3).
        withContext(NonCancellable) {
            pendingPhoto.save(jpeg)
            follower.follow(job.jobId)
        }
        _state.update { it.copy(phase = SetupPhase.WORKING) }
    }

    /** 보내지 못했다: 고르기로 돌아가 사유를 보인다(사진은 버린다, 6.2 Sending → Choose). */
    private fun fail(error: AppError) {
        val code = (error as? AppError.Rejected)?.code
        _state.update {
            it.copy(
                phase = SetupPhase.CHOOSE,
                photo = null,
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

    /** 새 얼굴 화면의 「확인」: 결과를 본 것으로 두고 돌아간다. */
    fun onDoneConfirm() {
        avatars.dismissSelfieResult()
        done()
    }

    /** 실패 화면의 「다른 사진 고르기」: 실패를 지우고 고르기로(같은 날 다시 할 수 있다, 실패는 횟수에 세지 않는다). */
    fun onRetry() {
        avatars.dismissSelfieResult()
        _state.update { it.copy(phase = SetupPhase.CHOOSE, photo = null, failure = null) }
    }

    /** 실패 화면의 「기본 얼굴 쓰기」: 실패를 지우고 고르기 화면의 기본 얼굴로. */
    fun onUseDefault() = onRetry()

    /**
     * 기본 얼굴 저장: 서버에 기본 캐릭터 [DefaultCharacter.DEFAULT](RABBIT)를 저장한다([임시:D-성-187]). 셀카 얼굴을 만드는 중에도 저장할 수
     * 있다(FR-158): 서버는 그 뒤 늦게 끝난 셀카 결과를 기본 캐릭터 위에 반영하지 않으므로 따라가기를 멈추고 보낸 사진을 지운다.
     */
    fun onConfirmDefaultFace() {
        if (!_state.value.isDefaultSelected || _state.value.isWorking) return
        _state.update { it.copy(isWorking = true, saveError = null) }
        viewModelScope.launch {
            when (val result = avatars.chooseDefault(DefaultCharacter.DEFAULT.name)) {
                is Outcome.Success -> {
                    follower.cancel()
                    pendingPhoto.clear()
                    _state.update { it.copy(isWorking = false, phase = SetupPhase.CHOOSE, photo = null) }
                    done()
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isWorking = false, saveError = AvatarErrorText.of(result.error))
                }
            }
        }
    }

    private var isDone = false

    /** 이전 화면으로 돌아간다. 한 번만. */
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
    }
}
