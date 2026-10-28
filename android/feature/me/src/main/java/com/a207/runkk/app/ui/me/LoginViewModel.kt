package com.a207.runkk.app.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.LoginUseCase
import com.a207.runkk.app.domain.account.ProviderToken
import com.a207.runkk.app.domain.account.ProviderType
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
 * 로그인(FR-001). ProviderType으로 고른 SocialLoginClient로 토큰을 받아 LoginUseCase에 넘긴다(6.1.14).
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val clients: Set<@JvmSuppressWildcards SocialLoginClient>,
    private val login: LoginUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    /**
     * 로그인 시작: 고른 제공자의 SDK 창구를 돌려준다. 이미 진행 중이거나 창구가 없으면 null. SDK 로그인 창은 화면(LoginRoute)이 자기
     * 코루틴에서 Activity로 띄우고 ViewModel은 Activity Context를 잡지 않는다(AN5-D-11, 설정의 연동과 같은 구조).
     * 받은 토큰은 [onLoginToken], 화면이 사라져 끊기면 [onLoginCancelled]로 넘긴다.
     */
    fun onLoginStart(provider: ProviderType): SocialLoginClient? {
        if (_state.value.inProgress != null) return null
        val client = clients.firstOrNull { it.provider == provider } ?: return null
        _state.update { it.copy(inProgress = provider, error = null) }
        return client
    }

    /** 제공자 토큰(실패 포함)을 받아 LoginUseCase로 로그인한다. */
    fun onLoginToken(token: Outcome<ProviderToken>) {
        viewModelScope.launch {
            val result = when (token) {
                is Outcome.Failure -> token
                is Outcome.Success -> login(token.value)
            }
            _state.update { it.copy(inProgress = null) }
            when (result) {
                is Outcome.Success -> _events.send(LoginEvent.LoggedIn)
                is Outcome.Failure -> onFailure(result.error)
            }
        }
    }

    /** 화면이 사라져 SDK 로그인이 끊겼다: 진행 표시만 푼다(로그인하지 않았다). */
    fun onLoginCancelled() = _state.update { it.copy(inProgress = null) }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    private suspend fun onFailure(error: AppError) {
        if (error is AppError.Rejected && error.code == AppError.Rejected.SIGN_UP_REQUIRED) {
            _events.send(LoginEvent.SignUpRequired)
        } else {
            _state.update { it.copy(error = RunkkErrorText.of(error)) }
        }
    }
}
