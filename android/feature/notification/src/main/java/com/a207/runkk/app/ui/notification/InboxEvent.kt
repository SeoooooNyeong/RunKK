package com.a207.runkk.app.ui.notification

import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.navigation.route

sealed interface InboxEvent {
    /** 알림 이동 표(inboxDestination)로 고른 화면으로 간다. */
    data class Navigate(val key: NavKey) : InboxEvent
}
