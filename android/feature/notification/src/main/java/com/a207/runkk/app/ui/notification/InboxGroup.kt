package com.a207.runkk.app.ui.notification

import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import com.a207.runkk.app.domain.notification.Inbox

/** 알림 묶음(오늘·이번 주·그 전). */
data class InboxGroup(@StringRes val titleRes: Int, val items: List<Inbox.Item>)
