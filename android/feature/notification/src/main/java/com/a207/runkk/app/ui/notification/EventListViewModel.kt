package com.a207.runkk.app.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.notification.Event
import com.a207.runkk.app.domain.notification.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 이벤트 목록(이벤트 계약 2절): EventRepository.events. 목록을 읽으면 진행 중 이벤트를 본 것으로 기기에 남긴다(알림함·홈의 새 이벤트 점).
 */
@HiltViewModel
class EventListViewModel @Inject constructor(private val events: EventRepository, private val clock: Clock) :
    ViewModel() {
    private val _state = MutableStateFlow(EventListUiState())
    val state: StateFlow<EventListUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var resumedOnce = false

    init {
        refresh()
    }

    /** 상세나 셀럽런에서 돌아오면 응모 수와 상태를 다시 읽는다. 처음 표시는 init이 읽는다. */
    fun onResumed() {
        if (resumedOnce) refresh()
        resumedOnce = true
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            when (val result = events.events()) {
                is Outcome.Success -> {
                    val list = result.value
                    _state.update {
                        it.copy(isLoading = false, events = list, today = LocalDate.now(clock.withZone(EVENT_ZONE)))
                    }
                    val ongoing = list.filter { it.status == Event.Status.ONGOING }.map { it.id }.toSet()
                    events.markSeen(ongoing, list.map { it.id }.toSet())
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }

    fun onTab(tab: EventTab) = _state.update { it.copy(tab = tab) }

    /** 배너·썸네일 그림 바이트(EventImage가 부른다). */
    suspend fun image(url: String): Outcome<ByteArray> = events.image(url)
}

/** 카드·배너를 누르면 여는 상세 웹 페이지 경로. 상세 주소가 없으면(서버가 상대 경로를 주지 않음) null이다. */
internal fun eventDetailKey(event: Event): Routes.EventDetail? =
    event.detailUrl?.let { Routes.EventDetail(slug = event.slug, title = event.title, url = it) }

/** 이벤트 날짜(D-day, 기간)의 시간대. 서버 시각이 서울 기준이다. */
internal val EVENT_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
