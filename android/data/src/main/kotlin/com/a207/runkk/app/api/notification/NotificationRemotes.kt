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
        Inbox.Item(
            id = it.id,
            type = it.type,
            title = TITLES[it.type] ?: DEFAULT_TITLE,
            body = it.content,
            receivedAt = ServerTime.parse(it.occurredAt),
            read = it.read,
            route = it.target?.screen?.let(ROUTES::get)
        )
    }
)

private const val DEFAULT_TITLE = "알림"

/** 서버 NotificationType 이름 → 알림함 제목(서버는 내용만 준다). */
internal val TITLES: Map<String, String> = mapOf(
    "PLAN_MISSED" to "계획 미이행",
    "BODY_SHAPE_CHANGED" to "체형 변화",
    "JOIN_REQUESTED" to "크루 가입 신청",
    "JOIN_DECIDED" to "크루 가입 결과",
    "JOIN_EXPIRED" to "크루 가입 신청 만료",
    "LEADER_TRANSFERRED" to "크루장 양도",
    "CREW_DELETED" to "크루 삭제",
    "KICKED" to "크루 강제 퇴장",
    "CREW_RUN_RECRUIT" to "크루런 모집",
    "CREW_RUN_SOON" to "크루런 시작 전",
    "OPPONENT_ARRIVED" to "대결 상대 도착",
    "BATTLE_CORRECTED" to "대결 결과 정정"
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
