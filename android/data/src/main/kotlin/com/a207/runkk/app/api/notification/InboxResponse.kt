package com.a207.runkk.app.api.notification

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * 서버 notification.InboxView(최신순). 서버 항목 이름은 type·title·content·occurredAt·read·target(화면 종류 screen, 대상 targetId)이다.
 * 앱은 제목을 서버 title(운영자 공지만)이나 알림 종류에서, 이동 경로를 target.screen에서 만든다(NotificationRemotes).
 */
@Serializable
internal data class InboxResponse(val items: List<Item> = emptyList()) {
    @Serializable
    data class Item(
        val id: Long,
        val type: String,
        val title: String? = null,
        val content: String,
        val occurredAt: String,
        val read: Boolean = false,
        val target: Target? = null
    )

    @Serializable
    data class Target(val screen: String, val targetId: String? = null)
}
