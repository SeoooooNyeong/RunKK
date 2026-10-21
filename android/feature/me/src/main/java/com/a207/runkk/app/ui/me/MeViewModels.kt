package com.a207.runkk.app.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.account.AccountRepository
import com.a207.runkk.app.domain.account.LogoutUseCase
import com.a207.runkk.app.domain.account.Profile
import com.a207.runkk.app.domain.account.WithdrawUseCase
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

// ---------- 마이(프로필 요약·메뉴·로그아웃) ----------

data class MeUiState(
    val loading: Boolean = true,
    val profile: Profile? = null,
    /** 프로필 원 안의 셀카 얼굴 부위 그림. 비어 있으면 빈 원(자리 그림). */
    val faceLayers: List<FaceLayer> = emptyList(),
    val error: UiText? = null,
    val loggingOut: Boolean = false
)

sealed interface MeEvent {
    data object LoggedOut : MeEvent
}

/** 마이(FR-005, FR-002): 본인 프로필을 읽고 로그아웃한다. */
@HiltViewModel
class MeViewModel @Inject constructor(private val account: AccountRepository, private val logout: LogoutUseCase) :
    ViewModel() {
    private val _state = MutableStateFlow(MeUiState())
    val state: StateFlow<MeUiState> = _state.asStateFlow()

    private val _events = Channel<MeEvent>(Channel.BUFFERED)
    val events: Flow<MeEvent> = _events.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = account.profile(AccountRepository.SELF)) {
                is Outcome.Success -> {
                    _state.update { it.copy(loading = false, profile = result.value) }
                    val face = result.value.faceKey?.let { account.faceLayers(it).getOrNull() }.orEmpty()
                    _state.update { it.copy(faceLayers = face) }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }

    fun onLogoutConfirmed() {
        if (_state.value.loggingOut) return
        _state.update { it.copy(loggingOut = true) }
        viewModelScope.launch {
            val result = logout()
            _state.update { it.copy(loggingOut = false) }
            when (result) {
                is Outcome.Success -> _events.send(MeEvent.LoggedOut)
                is Outcome.Failure -> _state.update { it.copy(error = RunkkErrorText.of(result.error)) }
            }
        }
    }
}

// ---------- 프로필 편집 ----------

data class ProfileEditUiState(
    val loading: Boolean = true,
    val nickname: String = "",
    val intro: String = "",
    val tags: Set<String> = emptySet(),
    val saving: Boolean = false,
    val error: UiText? = null
) {
    val canSave: Boolean get() = !loading && !saving && nickname.trim().length in SignUpUiState.NICKNAME_LENGTH &&
        tags.isNotEmpty()
}

sealed interface ProfileEditEvent {
    data object Saved : ProfileEditEvent
}

/** 프로필 편집(FR-007): 닉네임·소개·성향 태그. 검증(중복·길이·태그 규칙)은 서버가 하고 거부 사유를 보인다. */
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
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = account.profile(AccountRepository.SELF)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        loading = false,
                        nickname = result.value.displayName,
                        intro = result.value.intro.orEmpty(),
                        tags = result.value.tags
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }

    fun onNicknameChange(value: String) = _state.update {
        it.copy(nickname = value.take(SignUpUiState.NICKNAME_LENGTH.last))
    }

    fun onIntroChange(value: String) = _state.update { it.copy(intro = value.take(SignUpUiState.INTRO_MAX)) }

    fun onTagToggled(id: String) = _state.update { it.copy(tags = RunkkTags.toggle(it.tags, id)) }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    fun onSave() {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val result = account.editProfile(s.nickname.trim(), s.intro.trim().ifEmpty { null }, s.tags)) {
                is Outcome.Success -> {
                    _state.update { it.copy(saving = false) }
                    _events.send(ProfileEditEvent.Saved)
                }

                is Outcome.Failure -> _state.update { it.copy(saving = false, error = RunkkErrorText.of(result.error)) }
            }
        }
    }
}

// ---------- 회원 탈퇴 ----------

data class WithdrawUiState(
    val acknowledged: Boolean = false,
    val withdrawing: Boolean = false,
    val error: UiText? = null
)

sealed interface WithdrawEvent {
    data object Withdrawn : WithdrawEvent
}

/**
 * 회원 탈퇴(FR-020, FR-021, FR-024). 삭제될 자료를 보여 주고 확인을 받은 뒤 WithdrawUseCase를 부른다.
 * 진행 중 대결·크루장 거부는 서버 사유 코드로 오고 그 문구를 보인다.
 */
@HiltViewModel
class WithdrawViewModel @Inject constructor(private val withdraw: WithdrawUseCase) : ViewModel() {
    private val _state = MutableStateFlow(WithdrawUiState())
    val state: StateFlow<WithdrawUiState> = _state.asStateFlow()

    private val _events = Channel<WithdrawEvent>(Channel.BUFFERED)
    val events: Flow<WithdrawEvent> = _events.receiveAsFlow()

    fun onAcknowledgedChange(value: Boolean) = _state.update { it.copy(acknowledged = value) }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    /** 확인 대화상자에서 "탈퇴"를 누른 뒤. 안내를 확인하지 않았으면 보내지 않는다. */
    fun onWithdrawConfirmed() {
        val s = _state.value
        if (!s.acknowledged || s.withdrawing) return
        _state.update { it.copy(withdrawing = true, error = null) }
        viewModelScope.launch {
            when (val result = withdraw()) {
                is Outcome.Success -> {
                    _state.update { it.copy(withdrawing = false) }
                    _events.send(WithdrawEvent.Withdrawn)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(withdrawing = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }
}
