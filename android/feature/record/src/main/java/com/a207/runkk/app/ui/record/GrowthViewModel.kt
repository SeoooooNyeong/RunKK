package com.a207.runkk.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.RecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class GrowthViewModel @Inject constructor(private val records: RecordRepository) : ViewModel() {
    private val _state = MutableStateFlow(GrowthUiState())
    val state: StateFlow<GrowthUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onPeriodSelected(period: AggregationPeriod) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period) }
        refresh()
    }

    fun refresh() {
        val period = _state.value.period
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = records.growth(period)) {
                is Outcome.Success -> _state.update { it.copy(isLoading = false, growth = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }
}
