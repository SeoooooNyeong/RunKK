package com.a207.runkk.app.ui.notification

import androidx.annotation.StringRes
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.domain.notification.GatedFeature

/** 알림 종류(서버 NotificationType 이름) → 알림함 줄 위 작은 종류 라벨(F7). 모르는 종류는 null(서버 제목을 쓴다). */
internal object InboxLabels {
    private val labels: Map<String, Int> = mapOf(
        "OPPONENT_ARRIVED" to R.string.notification_label_battle,
        "BATTLE_CORRECTED" to R.string.notification_label_battle,
        "PLAN_MISSED" to R.string.notification_label_plan,
        "BODY_SHAPE_CHANGED" to R.string.notification_label_avatar,
        "BACKGROUND_UNLOCKED" to R.string.notification_label_background,
        "JOIN_REQUESTED" to R.string.notification_label_crew,
        "JOIN_DECIDED" to R.string.notification_label_crew,
        "JOIN_EXPIRED" to R.string.notification_label_crew,
        "LEADER_TRANSFERRED" to R.string.notification_label_crew,
        "CREW_DELETED" to R.string.notification_label_crew,
        "KICKED" to R.string.notification_label_crew,
        "MANAGER_CHANGED" to R.string.notification_label_crew,
        "REPORT_RESOLVED" to R.string.notification_label_crew,
        "CREW_RUN_RECRUIT" to R.string.notification_label_crew_run,
        "CREW_RUN_SOON" to R.string.notification_label_crew_run,
        "CREW_NOTICE" to R.string.notification_label_crew_notice,
        "CREW_POLL" to R.string.notification_label_crew_poll,
        "POLL_CLOSING_SOON" to R.string.notification_label_crew_poll,
        "CREW_SCHEDULE" to R.string.notification_label_crew_schedule,
        "CREW_CHAT" to R.string.notification_label_crew_chat,
        "CREW_GOAL_ACHIEVED" to R.string.notification_label_crew_goal
    )

    @StringRes
    fun of(type: String): Int? = labels[type]

    /**
     * 1차 배포에서 닫은 기능의 알림인가: 아바타(체형·배경)와 크루 알림은 그 기능이 열려야 보인다(사용자 요청 2026-10-31). 종류 분류는
     * 시스템 푸시와 같은 domain.notification.GatedFeature다(D-339).
     */
    fun isVisibleIn(type: String, scope: ReleaseScope): Boolean = when (GatedFeature.of(type)) {
        GatedFeature.AVATAR_CUSTOMIZE -> scope.isOpen(UnreleasedFeature.AVATAR_CUSTOMIZE)
        GatedFeature.CREW -> scope.isOpen(UnreleasedFeature.CREW)
        null -> true
    }
}
