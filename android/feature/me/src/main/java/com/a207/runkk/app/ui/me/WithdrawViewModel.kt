package com.a207.runkk.app.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Outcome
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

    fun onAcknowledgedChange(isAcknowledged: Boolean) = _state.update { it.copy(isAcknowledged = isAcknowledged) }

    fun onErrorShown() = _state.update { it.copy(error = null) }

    /** 확인 대화상자에서 "탈퇴"를 누른 뒤. 안내를 확인하지 않았으면 보내지 않는다. */
    fun onWithdrawConfirmed() {
        val s = _state.value
        if (!s.isAcknowledged || s.isWithdrawing) return
        _state.update { it.copy(isWithdrawing = true, error = null) }
        viewModelScope.launch {
            when (val result = withdraw()) {
                is Outcome.Success -> {
                    _state.update { it.copy(isWithdrawing = false) }
                    _events.send(WithdrawEvent.Withdrawn)
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isWithdrawing = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }
}
