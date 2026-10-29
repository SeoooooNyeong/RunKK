package com.a207.runkk.app.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.takeCodePoints
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.ProfileEdit
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
 * 프로필 편집(G-2, D-287, FR-007·FR-012): 닉네임·소개와 나이대·성별 공개 스위치를 한 번에 PATCH /me/profile로 보낸다
 * (AccountRepository.editProfile(ProfileEdit)). 검증(중복·길이)은 서버가 하고 거부 사유를 보인다.
 * 공개 여부는 본인 프로필 응답의 ageBandVisibility·genderVisibility로 채우고, 옛 서버라 없으면 비공개로 둔다(D-289).
 */
@HiltViewModel
class ProfileEditViewModel @Inject constructor(private val account: AccountRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProfileEditUiState())
    val state: StateFlow<ProfileEditUiState> = _state.asStateFlow()

    private val _events = Channel<ProfileEditEvent>(Channel.BUFFERED)
    val events: Flow<ProfileEditEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = account.profile(AccountRepository.SELF)) {
                is Outcome.Success -> _state.update {
                    val me = result.value
                    it.copy(
                        isLoading = false,
                        nickname = me.displayName,
                        intro = me.intro.orEmpty(),
                        ageBand = me.ageBand,
                        gender = me.gender,
                        isAgePublic = me.isAgePublic ?: false,
                        isGenderPublic = me.isGenderPublic ?: false
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

    fun onAgePublicChange(isPublic: Boolean) = _state.update { it.copy(isAgePublic = isPublic) }

    fun onGenderPublicChange(isPublic: Boolean) = _state.update { it.copy(isGenderPublic = isPublic) }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    fun onSave() {
        val s = _state.value
        if (s.isSaving) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val edit = ProfileEdit(
                nickname = s.nickname.trim(),
                intro = s.intro.trim().ifEmpty { null },
                isAgePublic = s.isAgePublic,
                isGenderPublic = s.isGenderPublic
            )
            when (val saved = account.editProfile(edit)) {
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
}
