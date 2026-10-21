package com.a207.runkk.app.domain.notification

import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/**
 * 알림함(서버 notification.InboxView). 최신순이고 화면이 오늘·이번 주·그 전으로 묶는다(FR-178).
 * route는 알림을 눌렀을 때 갈 core.navigation 경로 이름이다. 필드는 10/21 API 계약으로 확정한다.
 */
data class Inbox(val items: List<Item>) {
    data class Item(
        val id: Long,
        val type: String,
        val title: String,
        val body: String,
        val receivedAt: Instant,
        val read: Boolean,
        val route: String?
    )
}
