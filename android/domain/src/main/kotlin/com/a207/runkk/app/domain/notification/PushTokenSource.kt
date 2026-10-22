package com.a207.runkk.app.domain.notification

import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/** FCM 등록 토큰 읽기 포트. 구현 data.push.FcmPushTokenSource. */
interface PushTokenSource {
    suspend fun currentToken(): String?
}
