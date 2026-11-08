package com.a207.runkk.app.ui.notification

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.route
import com.a207.runkk.app.domain.notification.Inbox
import com.a207.runkk.app.domain.notification.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 알림함(FR-175·FR-178): NotificationRepository.inbox·open. 누르면 읽음으로 하고 알림 이동 표의 화면으로 간다. */
@HiltViewModel
class InboxViewModel internal constructor(
    private val notifications: NotificationRepository,
    private val clock: Clock,
    private val zone: ZoneId
) : ViewModel() {
    /** 앱 Clock은 UTC(DeviceBindings)라 날짜·시각은 기기 시간대 [zone](ZoneId.systemDefault(), 러닝 결과 화면과 같다)으로 셈한다(MR !91 e2e 2 후속). */
    @Inject
    constructor(notifications: NotificationRepository, clock: Clock) :
        this(notifications, clock, ZoneId.systemDefault())

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    private val _events = Channel<InboxEvent>(Channel.BUFFERED)
    val events: Flow<InboxEvent> = _events.receiveAsFlow()

    init {
        refresh()
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
        // 모르는 화면이면 알림함에 머문다(옛 앱 호환, FR-178).
        inboxDestination(item.screen, item.targetId)?.let { _events.trySend(InboxEvent.Navigate(it)) }
    }
}
