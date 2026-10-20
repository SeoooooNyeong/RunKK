package com.a207.runkk.app.domain.notification

import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/** 알림함 포트(GET /notifications, POST /notifications/{id}/open). 구현 api.notification. */
interface NotificationRepository {
    suspend fun inbox(): Outcome<Inbox>

    suspend fun open(notificationId: Long): Outcome<Unit>
}

/** 푸시 기기 토큰 등록 포트(PUT /me/push-token, D-43). 구현 api.notification. */
interface PushTokenRemote {
    suspend fun register(token: String): Outcome<Unit>
}

/** FCM 등록 토큰 읽기 포트. 구현 data.push.FcmPushTokenSource. */
interface PushTokenSource {
    suspend fun currentToken(): String?
}

/**
 * 로그인 상태 포트. 구현 data.session.EncryptedTokenStore.
 * domain.account의 SessionRepository 대신 자기 포트로 읽어 domain.notification → domain.account가 없다(design-review.md E19).
 */
interface LoginState {
    val loggedIn: StateFlow<Boolean>
}

/**
 * 알림함(서버 notification.InboxView). 최신순이고 화면이 오늘·이번 주·그 전으로 묶는다(FR-178).
 * route는 알림을 눌렀을 때 갈 core.navigation 경로 이름이다. 필드는 10/21 API 계약으로 확정한다.
 */
data class Inbox(val items: List<Item>) {
    data class Item(
        val id: Long,
        val type: String,
        val title: String,
        val body: String,
        val receivedAt: Instant,
        val read: Boolean,
        val route: String?
    )
}
