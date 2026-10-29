package com.a207.runkk.app.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.account.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 다른 러너 프로필(F8, FR-005, D-287): GET /members/{id}/profile을 읽는다. 최근 러닝은 그 응답의 recentRuns로 보인다. */
@HiltViewModel
class OtherProfileViewModel @Inject constructor(private val account: AccountRepository) : ViewModel() {
    private val _state = MutableStateFlow(OtherProfileUiState())
    val state: StateFlow<OtherProfileUiState> = _state.asStateFlow()

    fun load(memberId: Long, isForced: Boolean = false) {
        if (!isForced && _state.value.memberId == memberId && _state.value.profile != null) return
        _state.value = OtherProfileUiState(memberId = memberId)
        viewModelScope.launch {
            when (val result = account.profile(memberId)) {
                is Outcome.Success -> {
                    _state.update { it.copy(isLoading = false, profile = result.value) }
                    val face = result.value.faceKey?.let { account.faceLayers(it).getOrNull() }.orEmpty()
                    _state.update { it.copy(faceLayers = face) }
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }

    fun retry() {
        _state.value.memberId?.let { load(it, isForced = true) }
    }
}
