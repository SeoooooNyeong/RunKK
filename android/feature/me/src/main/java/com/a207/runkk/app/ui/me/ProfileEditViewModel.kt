package com.a207.runkk.app.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.takeCodePoints
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.ChangeSettingUseCase
import com.a207.runkk.app.domain.account.SettingChangeRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 프로필 편집(F3, FR-007): 닉네임·소개와 나이대·성별·소개 공개. 검증(중복·길이)은 서버가 하고 거부 사유를 보인다.
 * 공개 여부는 설정 항목(PROFILE_VISIBILITY, FR-010)이라 저장할 때 바뀌었으면 ChangeSettingUseCase로 함께 보낸다.
 */
@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val account: AccountRepository,
    private val changeSetting: ChangeSettingUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileEditUiState())
    val state: StateFlow<ProfileEditUiState> = _state.asStateFlow()

    private val _events = Channel<ProfileEditEvent>(Channel.BUFFERED)
    val events: Flow<ProfileEditEvent> = _events.receiveAsFlow()

    /** 읽어 온 공개 여부(저장할 때 바뀌었는지 본다). */
    private var loadedPublic: Boolean? = null

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val key = SettingKeys.PROFILE_VISIBILITY
            val isPublic = account.settings().getOrNull()?.let {
                (it.values[key] ?: SettingKeys.DEFAULTS[key]) == PUBLIC
            }
            loadedPublic = isPublic
            _state.update { it.copy(isProfilePublic = isPublic) }
        }
        viewModelScope.launch {
            when (val result = account.profile(AccountRepository.SELF)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        nickname = result.value.displayName,
                        intro = result.value.intro.orEmpty()
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }

    fun onNicknameChange(value: String) = _state.update {
        it.copy(nickname = value.takeCodePoints(ProfileEditUiState.NICKNAME_LENGTH.last))
    }

    fun onIntroChange(value: String) = _state.update { it.copy(intro = value.take(ProfileEditUiState.INTRO_MAX)) }

    fun onProfilePublicChange(isPublic: Boolean) = _state.update { it.copy(isProfilePublic = isPublic) }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    fun onSave() {
        val s = _state.value
        if (s.isSaving) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = account.editProfile(s.nickname.trim(), s.intro.trim().ifEmpty { null })
            val visibility = s.isProfilePublic
            val saved = if (result is Outcome.Success && visibility != null && visibility != loadedPublic) {
                changeSetting(
                    SettingChangeRequest.Change(SettingKeys.PROFILE_VISIBILITY, if (visibility) PUBLIC else PRIVATE)
                )
            } else {
                result
            }
            when (saved) {
                is Outcome.Success -> {
                    _state.update { it.copy(isSaving = false) }
                    _events.send(ProfileEditEvent.Saved)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isSaving = false, error = RunkkErrorText.of(saved.error))
                }
            }
        }
    }

    private companion object {
        const val PUBLIC = "PUBLIC"
        const val PRIVATE = "PRIVATE"
    }
}
