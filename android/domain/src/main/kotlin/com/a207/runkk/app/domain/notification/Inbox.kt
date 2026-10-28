package com.a207.runkk.app.domain.notification

import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/**
 * 알림함(서버 notification.InboxView). 최신순이고 화면이 오늘·이번 주·그 전으로 묶는다(FR-178).
 * screen·targetId는 알림을 눌렀을 때 갈 화면(서버 notification.Screen 이름)과 대상 ID(크루·대결 ID 등)이다.
 * 화면 경로로 바꾸는 표는 ui.notification이 갖는다(알림 이동 표, D-150). 필드는 10/21 API 계약으로 확정한다.
 */
data class Inbox(val items: List<Item>) {
    data class Item(
        val id: Long,
        val type: String,
        val title: String,
        val body: String,
        val receivedAt: Instant,
        val isRead: Boolean,
        val screen: String?,
        val targetId: String? = null
    )
}
