package com.a207.runkk.app.ui.me

/**
 * 알림 설정 묶음(F5, FR-008, FR-175): 자료사전 알림종류(서버 이름)를 화면 스위치 9개로 묶는다. 키는 SettingKeys.alert(종류)다.
 * "이벤트, 공지"는 알림 종류가 아니라 마케팅 수신 동의다.
 */
object NotificationTypes {
    /** 러닝, 대결 묶음. */
    val running: List<NotificationGroup> = listOf(
        NotificationGroup(
            R.string.me_alert_group_battle,
            R.string.me_alert_group_battle_description,
            listOf("OPPONENT_ARRIVED", "BATTLE_CORRECTED")
        ),
        NotificationGroup(R.string.me_alert_group_plan, null, listOf("PLAN_MISSED")),
        NotificationGroup(
            R.string.me_alert_group_avatar,
            R.string.me_alert_group_avatar_description,
            listOf("BODY_SHAPE_CHANGED", "BACKGROUND_UNLOCKED")
        )
    )

    /** 크루 묶음. */
    val crew: List<NotificationGroup> = listOf(
        NotificationGroup(
            R.string.me_alert_group_crew_news,
            R.string.me_alert_group_crew_news_description,
            listOf(
                "JOIN_REQUESTED",
                "JOIN_DECIDED",
                "JOIN_EXPIRED",
                "LEADER_TRANSFERRED",
                "CREW_DELETED",
                "KICKED",
                "MANAGER_CHANGED",
                "REPORT_RESOLVED"
            )
        ),
        NotificationGroup(
            R.string.me_alert_group_crew_notice,
            R.string.me_alert_group_crew_notice_description,
            listOf("CREW_NOTICE", "CREW_SCHEDULE", "CREW_RUN_RECRUIT", "CREW_RUN_SOON")
        ),
        NotificationGroup(
            R.string.me_alert_group_crew_poll,
            R.string.me_alert_group_crew_poll_description,
            listOf("CREW_POLL", "POLL_CLOSING_SOON")
        ),
        NotificationGroup(R.string.me_alert_group_crew_chat, null, listOf("CREW_CHAT")),
        NotificationGroup(R.string.me_alert_group_crew_goal, null, listOf("CREW_GOAL_ACHIEVED"))
    )

    /** 기타 묶음. */
    val etc: List<NotificationGroup> = listOf(
        NotificationGroup(R.string.me_alert_group_event, null, emptyList(), consentItem = ConsentKeys.MARKETING)
    )

    /** 화면에 있는 모든 알림 종류(서버 알림종류 21개). */
    val allTypes: List<String> get() = (running + crew + etc).flatMap { it.types }
}
