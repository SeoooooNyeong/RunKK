package com.a207.runkk.app.ui.notification

import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.route
import javax.inject.Inject

/** 알림함 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14). */
class NotificationFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.NOTIFICATIONS

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.NOTIFICATIONS) { InboxRoute() }
    }
}
