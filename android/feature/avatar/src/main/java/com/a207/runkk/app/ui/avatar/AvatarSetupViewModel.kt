package com.a207.runkk.app.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.avatar.AvatarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
 * 아바타 만들기(FR-155~FR-158): 셀카(앱이 줄인 JPEG)를 createFromSelfie로 보내거나 기본 캐릭터 4종 중 하나를 chooseDefault로 고른다.
 * 셀카 실패(동의 없음·하루 1회 초과·변환 실패)는 사유와 함께 기본 캐릭터 선택을 안내한다.
 */
@HiltViewModel
class AvatarSetupViewModel internal constructor(
    private val avatars: AvatarRepository,
    /** 셀카 줄이기 디스패처(리뷰 N-10). */
    private val encodeDispatcher: CoroutineDispatcher
) : ViewModel() {
    @Inject
    constructor(avatars: AvatarRepository) : this(avatars, Dispatchers.Default)

    private val _state = MutableStateFlow(AvatarSetupUiState())
    val state: StateFlow<AvatarSetupUiState> = _state.asStateFlow()

    private val _events = Channel<AvatarSetupEvent>(Channel.BUFFERED)
    val events: Flow<AvatarSetupEvent> = _events.receiveAsFlow()

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
    }

    fun onCharacterSelected(character: DefaultCharacter) = _state.update { it.copy(selected = character, error = null) }

    /** 셀카 촬영을 취소했거나 사진을 읽지 못했을 때. */
    fun onSelfieUnavailable() = _state.update { it.copy(error = UiText.Res(R.string.avatar_setup_selfie_unavailable)) }

    /** 찍은 셀카를 줄여(긴 변 1024px·JPEG·1MB) 보낸다. 줄이는 동안에도 다시 누르지 못하게 먼저 isWorking을 켠다. */
    fun onSelfieCaptured(bitmap: android.graphics.Bitmap) {
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            val jpeg = withContext(encodeDispatcher) { SelfieEncoder.encode(bitmap) }
            upload(jpeg)
        }
    }

    fun onSelfie(jpeg: ByteArray) {
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch { upload(jpeg) }
    }

    private suspend fun upload(jpeg: ByteArray) {
        when (val result = avatars.createFromSelfie(jpeg)) {
            is Outcome.Success -> {
                _state.update { it.copy(isWorking = false) }
                _events.trySend(AvatarSetupEvent.Done)
            }

            is Outcome.Failure -> {
                val code = (result.error as? AppError.Rejected)?.code
                _state.update {
                    it.copy(
                        isWorking = false,
                        canCreateSelfie = it.canCreateSelfie && code !in SELFIE_BLOCKED,
                        error = AvatarErrorText.of(result.error)
                    )
                }
            }
        }
    }

    fun onConfirmCharacter() {
        val character = _state.value.selected ?: return
        if (_state.value.isWorking) return
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
}
