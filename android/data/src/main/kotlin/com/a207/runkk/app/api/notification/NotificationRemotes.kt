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

/** titleOf는 서버 type → 알림함 제목(문자열 리소스, TITLES). 서버가 제목을 주면(운영자 공지) 그 제목을 쓴다. */
internal fun InboxResponse.toDomain(titleOf: (type: String) -> String): Inbox = Inbox(
    items.map {
        Inbox.Item(
            id = it.id,
            type = it.type,
            title = it.title?.takeIf(String::isNotBlank) ?: titleOf(it.type),
            body = it.content,
            receivedAt = ServerTime.parse(it.occurredAt),
            isRead = it.read,
            screen = it.target?.screen,
            targetId = it.target?.targetId
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
    "BATTLE_CORRECTED" to R.string.inbox_title_battle_corrected,
    "CREW_NOTICE" to R.string.inbox_title_crew_notice,
    "CREW_POLL" to R.string.inbox_title_crew_poll,
    "POLL_CLOSING_SOON" to R.string.inbox_title_poll_closing_soon,
    "CREW_SCHEDULE" to R.string.inbox_title_crew_schedule,
    "CREW_CHAT" to R.string.inbox_title_crew_chat,
    "CREW_GOAL_ACHIEVED" to R.string.inbox_title_crew_goal_achieved,
    "MANAGER_CHANGED" to R.string.inbox_title_manager_changed,
    "BACKGROUND_UNLOCKED" to R.string.inbox_title_background_unlocked,
    "REPORT_RESOLVED" to R.string.inbox_title_report_resolved,
    "NOTICE" to R.string.inbox_title_notice
)
