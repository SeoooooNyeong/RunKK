package com.a207.runkk.app.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.account.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 마이(F2, FR-005): 본인 프로필을 읽는다. 프로필 편집 저장 뒤 돌아오면(화면 재개) 다시 읽는다(AN8-3).
 * 로그아웃은 설정(SettingsViewModel)이 한다.
 */
@HiltViewModel
class MeViewModel @Inject constructor(private val account: AccountRepository) : ViewModel() {
    private val _state = MutableStateFlow(MeUiState())
    val state: StateFlow<MeUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var resumedOnce = false

    init {
        refresh()
    }

    /** 화면이 다시 보일 때(프로필 편집 저장 뒤 복귀 등) 다시 읽는다. 처음 표시는 init이 읽으므로 첫 재개는 건너뛴다. */
    fun onResumed() {
        if (resumedOnce) refresh()
        resumedOnce = true
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        // 늦게 끝난 앞 요청이 새 결과를 덮지 않게 취소한다.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            when (val result = account.profile(AccountRepository.SELF)) {
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
}
