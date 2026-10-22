package com.a207.runkk.app.data.push

import com.a207.runkk.app.domain.notification.PushTokenSync
import com.google.firebase.messaging.FirebaseMessagingService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * FCM 등록 토큰 변경을 받는다(6.1.14 data.push, D-43). onNewToken → PushTokenSync.onTokenChanged.
 * 알림 표시는 FCM 알림 메시지를 OS가 띄우므로 메시지 수신 처리는 없다. 토큰 값은 로그에 쓰지 않는다.
 * google-services.json이 없는 빌드에서는 Firebase가 초기화되지 않아 이 서비스가 불리지 않는다.
 */
@AndroidEntryPoint
class RunkkMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var pushTokenSync: PushTokenSync

    override fun onNewToken(token: String) {
        pushTokenSync.onTokenChanged(token)
    }
}
