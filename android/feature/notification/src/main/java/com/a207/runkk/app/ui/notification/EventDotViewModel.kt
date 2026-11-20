package com.a207.runkk.app.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.notification.Event
import com.a207.runkk.app.domain.notification.EventRepository
import com.a207.runkk.app.domain.notification.hasUnseenOngoing
import com.a207.runkk.app.domain.settings.RefreshGate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 홈 알림함 단추의 새 이벤트 점: 진행 중 이벤트 가운데 이 기기에서 아직 보지 않은 것이 있는가. 못 읽으면 지난 값을 둔다.
 * 이벤트 목록은 문지기(RefreshGate "events", 30초, Jira 325)를 거쳐 서버에서 읽는다. 30초 안에 다시 보이면 서버를 부르지 않고 지난
 * 목록과 기기의 본 목록으로 다시 센다.
 */
@HiltViewModel
class EventDotViewModel @Inject constructor(
    private val events: EventRepository,
    private val gate: RefreshGate,
    private val clock: Clock
) : ViewModel() {
    /** 시험용: 기본 문지기와 기기 시계. */
    internal constructor(events: EventRepository) : this(events, RefreshGate(), Clock.systemUTC())

    private val _hasUnseen = MutableStateFlow(false)
    val hasUnseen: StateFlow<Boolean> = _hasUnseen.asStateFlow()

    private var job: Job? = null

    /** 마지막으로 읽은 이벤트 목록. */
    private var known: List<Event>? = null

    fun refresh() {
        if (!gate.shouldRun(EVENTS_KEY, clock.instant(), force = false)) {
            if (job?.isActive != true) recount()
            return
        }
        job = viewModelScope.launch {
            var ok = false
            try {
                val list = (events.events() as? Outcome.Success)?.value ?: return@launch
                known = list
                ok = true
                _hasUnseen.value = list.hasUnseenOngoing(events.seenEventIds())
            } finally {
                gate.markDone(EVENTS_KEY, clock.instant(), ok)
            }
        }
    }

    /** 지난 목록으로 다시 센다(본 목록은 기기 값이라 서버를 부르지 않는다). */
    private fun recount() {
        val list = known ?: return
        job = viewModelScope.launch { _hasUnseen.value = list.hasUnseenOngoing(events.seenEventIds()) }
    }

    private companion object {
        const val EVENTS_KEY = "events"
    }
}
