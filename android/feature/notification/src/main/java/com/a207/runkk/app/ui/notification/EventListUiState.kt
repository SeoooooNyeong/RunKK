package com.a207.runkk.app.ui.notification

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.notification.Event
import java.time.LocalDate

/**
 * 이벤트 목록(피그마 이벤트 모아보기). [events]는 서버 순서(진행 중, 곧 열림, 지난)다. [today]는 D-day 기준 날짜(서울)이고
 * 읽기 전에는 null이다.
 */
data class EventListUiState(
    val isLoading: Boolean = true,
    val events: List<Event> = emptyList(),
    val tab: EventTab = EventTab.ONGOING,
    val today: LocalDate? = null,
    val error: UiText? = null
) {
    /** 지금 탭의 카드 목록. 진행 중 탭은 진행 중과 곧 열림, 지난 이벤트 탭은 끝난 이벤트다. */
    val visible: List<Event>
        get() = events.filter { (it.status == Event.Status.ENDED) == (tab == EventTab.PAST) }

    /** 맨 위 큰 배너: 진행 중 탭에서 배너 그림이 있는 진행 중 이벤트. */
    val banners: List<Event>
        get() = if (tab == EventTab.ONGOING) {
            events.filter { it.status == Event.Status.ONGOING && it.bannerImageUrl != null }
        } else {
            emptyList()
        }
}
