package com.a207.runkk.app.ui.notification

import com.a207.runkk.app.core.designsystem.UiText
import java.time.Instant
import java.time.ZoneId

data class InboxUiState(
    val isLoading: Boolean = true,
    val groups: List<InboxGroup> = emptyList(),
    val error: UiText? = null,
    val message: UiText? = null,
    /** 상대 시간("12분 전")의 기준 시각과 시간대. [now]가 없으면 날짜와 시각을 보인다. */
    val now: Instant? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    /** 맨 위 이벤트 항목. 이벤트를 못 읽었거나 하나도 없으면 null이고 항목을 숨긴다. */
    val eventSummary: InboxEventSummary? = null
) {
    val unreadCount: Int get() = groups.sumOf { g -> g.items.count { !it.isRead } }
}
