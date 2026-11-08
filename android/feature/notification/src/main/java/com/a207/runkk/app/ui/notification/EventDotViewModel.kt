package com.a207.runkk.app.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.notification.EventRepository
import com.a207.runkk.app.domain.notification.hasUnseenOngoing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 홈 알림함 단추의 새 이벤트 점: 진행 중 이벤트 가운데 이 기기에서 아직 보지 않은 것이 있는가. 못 읽으면 지난 값을 둔다. */
@HiltViewModel
class EventDotViewModel @Inject constructor(private val events: EventRepository) : ViewModel() {
    private val _hasUnseen = MutableStateFlow(false)
    val hasUnseen: StateFlow<Boolean> = _hasUnseen.asStateFlow()

    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            val list = (events.events() as? Outcome.Success)?.value ?: return@launch
            _hasUnseen.value = list.hasUnseenOngoing(events.seenEventIds())
        }
    }
}
