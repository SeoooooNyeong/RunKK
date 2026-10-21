package com.a207.runkk.app.api.notification

import com.a207.runkk.app.api.core.ApiErrorMapper
import com.a207.runkk.app.api.core.ServerTime
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.notification.Inbox
import com.a207.runkk.app.domain.notification.NotificationRepository
import com.a207.runkk.app.domain.notification.PushTokenRemote
import javax.inject.Inject

/** 알림함 포트(GET /notifications, POST /notifications/{id}/open)를 구현한다(6.1.14 api.notification). */
internal class NotificationRepositoryImpl
@Inject
constructor(private val api: NotificationApi) :
    NotificationRepository {
    override suspend fun inbox(): Outcome<Inbox> = ApiErrorMapper.call { api.inbox().toDomain() }

    override suspend fun open(notificationId: Long): Outcome<Unit> = ApiErrorMapper.call { api.open(notificationId) }
}

/** 푸시 기기 토큰 등록 포트(PUT /me/push-token)를 구현한다(D-43). 토큰 값은 로그에 쓰지 않는다. */
internal class PushTokenRemoteImpl
@Inject
constructor(private val api: NotificationApi) : PushTokenRemote {
    override suspend fun register(token: String): Outcome<Unit> =
        ApiErrorMapper.call { api.registerPushToken(PushTokenRequest(token)) }
}

internal fun InboxResponse.toDomain(): Inbox = Inbox(
    items.map {
        Inbox.Item(it.id, it.type, it.title, it.body, ServerTime.parse(it.receivedAt), it.read, it.route)
    }
)
