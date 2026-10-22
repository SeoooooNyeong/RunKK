package com.a207.runkk.app.domain.notification

import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/** 알림함 포트(GET /notifications, POST /notifications/{id}/open). 구현 api.notification. */
interface NotificationRepository {
    suspend fun inbox(): Outcome<Inbox>

    suspend fun open(notificationId: Long): Outcome<Unit>
}
