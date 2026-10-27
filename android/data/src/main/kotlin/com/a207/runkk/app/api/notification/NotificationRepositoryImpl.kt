package com.a207.runkk.app.api.notification

import android.content.Context
import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.R
import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.notification.Inbox
import com.a207.runkk.app.domain.notification.NotificationRepository
import com.a207.runkk.app.domain.notification.PushTokenRemote
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** 알림함 포트(GET /notifications, POST /notifications/{id}/open)를 구현한다(6.1.14 api.notification). */
internal class NotificationRepositoryImpl
@Inject
constructor(
    private val api: NotificationApi,
    @ApplicationContext private val context: Context
) : NotificationRepository {
    override suspend fun inbox(): Outcome<Inbox> = ApiErrorMapper.call { api.inbox().toDomain(::titleOf) }

    private fun titleOf(type: String): String = context.getString(TITLES[type] ?: R.string.inbox_title_default)

    override suspend fun open(notificationId: Long): Outcome<Unit> = ApiErrorMapper.call { api.open(notificationId) }
}
