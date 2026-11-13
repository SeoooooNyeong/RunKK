package com.a207.runkk.app.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.avatar.AvatarBackgrounds
import com.a207.runkk.app.domain.avatar.AvatarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 랜드마크 배경 고르기(FR-201, FR-202): AvatarRepository.backgrounds()로 해금·잠김 목록을 보이고 chooseBackground(key)로 고른다.
 * 잠긴 배경은 보내지 않고 남은 거리를 안내한다. 서버가 잠김(BACKGROUND_LOCKED)으로 거부해도 같은 안내를 보인다.
 */
@HiltViewModel
class AvatarBackgroundViewModel @Inject constructor(
    private val avatars: AvatarRepository,
    private val myAvatar: MyAvatarStore
) : ViewModel() {
    private val _state = MutableStateFlow(AvatarBackgroundUiState())
    val state: StateFlow<AvatarBackgroundUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = avatars.backgrounds()) {
                is Outcome.Success -> _state.update { it.copy(isLoading = false, backgrounds = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = AvatarErrorText.of(result.error))
                }
            }
        }
    }

    /** 랜드마크 배경을 고른다. 잠겨 있으면 보내지 않고 해금 거리를 안내한다. */
    fun onChoose(item: AvatarBackgrounds.Item) {
        if (!item.isUnlocked) {
            _state.update { it.copy(message = lockedText(item)) }
            return
        }
        choose(item.key)
    }

    /** 기본 배경으로 돌린다. */
    fun onChooseDefault() = choose(null)

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private fun choose(key: String?) {
        val s = _state.value
        val current = s.backgrounds ?: return
        if (s.choosingKey != null || current.selectedKey == key) return
        _state.update { it.copy(choosingKey = key ?: AvatarBackgroundUiState.DEFAULT_KEY) }
        viewModelScope.launch {
            when (val result = avatars.chooseBackground(key)) {
                is Outcome.Success -> {
                    _state.update {
                        it.copy(
                            choosingKey = null,
                            backgrounds = it.backgrounds?.copy(selectedKey = key),
                            message = UiText.Res(R.string.avatar_background_chosen)
                        )
                    }
                    // 홈·러닝 칸의 배경도 바꾼다(design-oo 05-30 3절 6단계).
                    myAvatar.refresh()
                }

                is Outcome.Failure -> _state.update {
                    it.copy(choosingKey = null, message = AvatarErrorText.of(result.error))
                }
            }
        }
    }

    private fun lockedText(item: AvatarBackgrounds.Item): UiText =
        UiText.Res(R.string.avatar_background_locked_hint, listOf(item.name, remainingKm(item.remaining)))
}
