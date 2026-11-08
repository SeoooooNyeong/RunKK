package com.a207.runkk.app.ui.notification

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.route
import com.a207.runkk.app.domain.notification.Event
import com.a207.runkk.app.domain.notification.EventRepository
import com.a207.runkk.app.domain.notification.Inbox
import com.a207.runkk.app.domain.notification.NotificationRepository
import com.a207.runkk.app.domain.notification.hasUnseenOngoing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 알림함(FR-175·FR-178): NotificationRepository.inbox·open. 누르면 읽음으로 하고 알림 이동 표의 화면으로 간다.
 * 맨 위 이벤트 항목은 EventRepository.events와 본 이벤트 사본으로 진행 중 수와 새 이벤트 점을 정한다(이벤트 계약 7절).
 */
@HiltViewModel
class InboxViewModel internal constructor(
    private val notifications: NotificationRepository,
    private val clock: Clock,
    private val eventRepository: EventRepository,
    private val zone: ZoneId
) : ViewModel() {
    /** 앱 Clock은 UTC(DeviceBindings)라 날짜·시각은 기기 시간대 [zone](ZoneId.systemDefault(), 러닝 결과 화면과 같다)으로 셈한다(MR !91 e2e 2 후속). */
    @Inject
    constructor(notifications: NotificationRepository, clock: Clock, eventRepository: EventRepository) :
        this(notifications, clock, eventRepository, ZoneId.systemDefault())

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    private val _events = Channel<InboxEvent>(Channel.BUFFERED)
    val events: Flow<InboxEvent> = _events.receiveAsFlow()

    private var resumedOnce = false
    private var eventsJob: Job? = null

    init {
        refresh()
        refreshEvents()
    }

    /** 화면이 다시 보일 때(이벤트 목록에서 돌아오면) 이벤트 항목을 다시 센다. 처음 표시는 init이 읽는다. */
    fun onResumed() {
        if (resumedOnce) refreshEvents()
        resumedOnce = true
    }

    /** 이벤트 항목. 못 읽으면 지난 값을 둔다(처음이면 항목 없음). */
    fun refreshEvents() {
        // 앞 읽기가 늦게 끝나 새 결과를 덮지 않게 취소한다
        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            val list = (eventRepository.events() as? Outcome.Success)?.value ?: return@launch
            val seen = eventRepository.seenEventIds()
            val summary = list.takeIf { it.isNotEmpty() }?.let {
                InboxEventSummary(it.count { e -> e.status == Event.Status.ONGOING }, it.hasUnseenOngoing(seen))
            }
            _state.update { it.copy(eventSummary = summary) }
        }
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = notifications.inbox()) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        groups = groupInbox(result.value.items, clock, zone),
                        now = clock.instant(),
                        zone = zone
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private fun markRead(id: Long, isRead: Boolean) = _state.update { s ->
        s.copy(
            groups = s.groups.map { g ->
                g.copy(
                    items = g.items.map {
                        if (it.id ==
                            id
                        ) {
                            it.copy(isRead = isRead)
                        } else {
                            it
                        }
                    }
                )
            }
        )
    }

    fun onItemTapped(item: Inbox.Item) {
        if (!item.isRead) {
            markRead(item.id, isRead = true)
            viewModelScope.launch {
                val opened = notifications.open(item.id)
                if (opened is Outcome.Failure) {
                    markRead(item.id, isRead = false)
                    _state.update { it.copy(message = RunkkErrorText.of(opened.error)) }
                }
            }
        }
        // 공지 링크는 바깥 브라우저로, 모르는 화면이면 알림함에 머문다(옛 앱 호환, FR-178).
        val link = inboxLink(item.screen, item.targetId)
        if (link != null) {
            _events.trySend(InboxEvent.OpenLink(link))
            return
        }
        inboxDestination(item.screen, item.targetId)?.let { _events.trySend(InboxEvent.Navigate(it)) }
    }
}
