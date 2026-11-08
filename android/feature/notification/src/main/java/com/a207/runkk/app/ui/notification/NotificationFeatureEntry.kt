package com.a207.runkk.app.ui.notification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.core.navigation.route
import javax.inject.Inject

/**
 * 알림함, 이벤트 목록, 이벤트 상세 웹 페이지 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14). 홈 알림함 단추의 새 이벤트 점
 * (칸 SlotKey.HOME_INBOX_DOT)을 넣는다(이벤트 계약 7절).
 */
class NotificationFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.NOTIFICATIONS

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.NOTIFICATIONS) { InboxRoute() }
        builder.route(Routes.EVENTS) { EventListRoute() }
        builder.addEntryProvider(Routes.EventDetail::class) { key -> EventDetailRoute(key) }
    }

    override val slots: Map<SlotKey, @Composable () -> Unit> = mapOf(SlotKey.HOME_INBOX_DOT to { HomeInboxDot() })
}
