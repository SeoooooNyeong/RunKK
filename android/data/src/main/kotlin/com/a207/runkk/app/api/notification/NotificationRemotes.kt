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

/** 푸시 기기 토큰 등록 포트(PUT /me/push-token)를 구현한다(D-43). 토큰 값은 로그에 쓰지 않는다. */
internal class PushTokenRemoteImpl
@Inject
constructor(private val api: NotificationApi) : PushTokenRemote {
    override suspend fun register(token: String): Outcome<Unit> =
        ApiErrorMapper.call { api.registerPushToken(PushTokenRequest(token)) }
}

/** titleOf는 서버 type → 알림함 제목(문자열 리소스, TITLES). */
internal fun InboxResponse.toDomain(titleOf: (type: String) -> String): Inbox = Inbox(
    items.map {
        Inbox.Item(
            id = it.id,
            type = it.type,
            title = titleOf(it.type),
            body = it.content,
            receivedAt = ServerTime.parse(it.occurredAt),
            isRead = it.read,
            route = it.target?.screen?.let(ROUTES::get)
        )
    }
)

/** 서버 NotificationType 이름 → 알림함 제목 문자열 리소스(서버는 내용만 준다). 모르는 type은 inbox_title_default. */
internal val TITLES: Map<String, Int> = mapOf(
    "PLAN_MISSED" to R.string.inbox_title_plan_missed,
    "BODY_SHAPE_CHANGED" to R.string.inbox_title_body_shape_changed,
    "JOIN_REQUESTED" to R.string.inbox_title_join_requested,
    "JOIN_DECIDED" to R.string.inbox_title_join_decided,
    "JOIN_EXPIRED" to R.string.inbox_title_join_expired,
    "LEADER_TRANSFERRED" to R.string.inbox_title_leader_transferred,
    "CREW_DELETED" to R.string.inbox_title_crew_deleted,
    "KICKED" to R.string.inbox_title_kicked,
    "CREW_RUN_RECRUIT" to R.string.inbox_title_crew_run_recruit,
    "CREW_RUN_SOON" to R.string.inbox_title_crew_run_soon,
    "OPPONENT_ARRIVED" to R.string.inbox_title_opponent_arrived,
    "BATTLE_CORRECTED" to R.string.inbox_title_battle_corrected
)

/**
 * 서버 notification.Screen 이름 → core.navigation 경로 이름(Routes). 대결 결과는 경로 이름만으로 대결 ID를 못 넘겨 대결 이력으로 연다.
 */
internal val ROUTES: Map<String, String> = mapOf(
    "COACH_PROGRESS" to "records/coach",
    "AVATAR" to "avatar",
    "CREW_MANAGE" to "crew",
    "CREW_HOME" to "crew",
    "CREW_RUN_RECRUIT" to "crew",
    "BATTLE_RESULT" to "battle/history"
)
