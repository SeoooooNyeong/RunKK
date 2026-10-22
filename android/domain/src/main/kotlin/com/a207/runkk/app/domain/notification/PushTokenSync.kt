package com.a207.runkk.app.domain.notification

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * FCM 등록 토큰을 서버에 등록한다(D-43, design-review.md E19).
 * 토큰 변경(RunkkMessagingService.onNewToken)은 로그인 상태일 때만 보내고, 로그아웃 상태면 로그인 뒤 syncCurrent가 보낸다.
 * 등록 실패는 던지지 않고 다음 앱 시작·다음 로그인 때 다시 보낸다.
 */
@Singleton
class PushTokenSync
@Inject
constructor(
    private val remote: PushTokenRemote,
    private val source: PushTokenSource,
    private val loginState: LoginState,
    private val scope: CoroutineScope
) {
    fun onTokenChanged(token: String) {
        if (!loginState.loggedIn.value) return
        scope.launch { remote.register(token) }
    }

    suspend fun syncCurrent() {
        if (!loginState.loggedIn.value) return
        val token = source.currentToken() ?: return
        remote.register(token)
    }
}
