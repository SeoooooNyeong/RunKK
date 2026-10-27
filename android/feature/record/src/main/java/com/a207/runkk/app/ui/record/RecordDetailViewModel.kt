package com.a207.runkk.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.RecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 기록 상세(C2, FR-059·FR-060·FR-078·FR-079·FR-185·FR-186). */
@HiltViewModel
class RecordDetailViewModel @Inject constructor(private val records: RecordRepository) : ViewModel() {
    private val _state = MutableStateFlow(RecordDetailUiState())
    val state: StateFlow<RecordDetailUiState> = _state.asStateFlow()

    fun load(recordId: String, isForced: Boolean = false) {
        if (!isForced && _state.value.recordId == recordId && _state.value.detail != null) return
        _state.value = RecordDetailUiState(recordId = recordId)
        viewModelScope.launch {
            when (val result = records.run(recordId)) {
                is Outcome.Success -> _state.update { it.copy(isLoading = false, detail = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = RecordErrorText.of(result.error))
                }
            }
        }
    }
}
