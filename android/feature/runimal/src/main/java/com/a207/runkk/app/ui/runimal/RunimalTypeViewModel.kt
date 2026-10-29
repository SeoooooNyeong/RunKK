package com.a207.runkk.app.ui.runimal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.runimal.RunimalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Runimal 유형 상세(G-6, FR-226, D-285): RunimalRepository.type으로 유형 하나의 설명·특징·러너 비율을 보인다. */
@HiltViewModel
class RunimalTypeViewModel @Inject constructor(private val runimal: RunimalRepository) : ViewModel() {
    private val _state = MutableStateFlow(RunimalTypeUiState())
    val state: StateFlow<RunimalTypeUiState> = _state.asStateFlow()
    private var job: Job? = null

    /** [type]을 불러온다. 이미 불러온 같은 유형이면 [isForced]일 때만 다시 부른다. */
    fun load(type: String, isForced: Boolean = false) {
        val s = _state.value
        if (!isForced && s.type == type && s.detail != null) return
        job?.cancel()
        // 다른 유형으로 바뀌면 앞 유형의 상세를 버린다.
        _state.update {
            it.copy(type = type, isLoading = true, error = null, detail = it.detail.takeIf { _ -> it.type == type })
        }
        job = viewModelScope.launch {
            when (val result = runimal.type(type)) {
                is Outcome.Success -> _state.update { it.copy(isLoading = false, detail = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }
}
