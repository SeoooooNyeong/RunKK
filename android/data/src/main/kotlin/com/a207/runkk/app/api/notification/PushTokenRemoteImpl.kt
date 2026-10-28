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

/** 푸시 기기 토큰 등록 포트(PUT /me/push-token)를 구현한다(D-43). 토큰 값은 로그에 쓰지 않는다. */
internal class PushTokenRemoteImpl
@Inject
constructor(private val api: NotificationApi) : PushTokenRemote {
    override suspend fun register(token: String): Outcome<Unit> =
        ApiErrorMapper.call { api.registerPushToken(PushTokenRequest(token)) }
}
