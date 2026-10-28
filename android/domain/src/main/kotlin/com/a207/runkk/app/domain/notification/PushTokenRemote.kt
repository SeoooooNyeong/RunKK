package com.a207.runkk.app.domain.notification

import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/** 푸시 기기 토큰 등록 포트(PUT /me/push-token, D-43). 구현 api.notification. */
interface PushTokenRemote {
    suspend fun register(token: String): Outcome<Unit>
}
