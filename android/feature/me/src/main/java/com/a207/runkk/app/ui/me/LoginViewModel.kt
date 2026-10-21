package com.a207.runkk.app.ui.me

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.LoginUseCase
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

/** 로그인 화면 상태. [inProgress]는 지금 로그인 중인 제공자. */
data class LoginUiState(val inProgress: ProviderType? = null, val error: String? = null)

sealed interface LoginEvent {
    data object LoggedIn : LoginEvent

    /** 회원이 아니면 단계별 가입으로(FR-001 "가입필요"). */
    data object SignUpRequired : LoginEvent
}

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

    fun onProviderTapped(provider: ProviderType, context: Context) {
        if (_state.value.inProgress != null) return
        val client = clients.firstOrNull { it.provider == provider } ?: return
        _state.update { it.copy(inProgress = provider, error = null) }
        viewModelScope.launch {
            val result = when (val token = client.signIn(context)) {
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

    fun onErrorShown() = _state.update { it.copy(error = null) }

    private suspend fun onFailure(error: AppError) {
        if (error is AppError.Rejected && error.code == AppError.Rejected.SIGN_UP_REQUIRED) {
            _events.send(LoginEvent.SignUpRequired)
        } else {
            _state.update { it.copy(error = RunkkErrorText.of(error)) }
        }
    }
}
