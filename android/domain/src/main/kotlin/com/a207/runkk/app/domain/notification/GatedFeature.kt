package com.a207.runkk.app.domain.notification

/**
 * 알림 종류(서버 NotificationType 이름)가 딸린 미공개 기능(1차 배포 잠금, D-325·D-339). 알림함(ui.notification InboxLabels)과
 * 시스템 푸시(data.push RunkkMessagingService)가 같은 판정을 쓴다. 딸린 기능이 없으면 [of]가 null이다(늘 보인다).
 */
enum class GatedFeature {
    /** 아바타 꾸미기(체형·배경 알림). */
    AVATAR_CUSTOMIZE,

    /** 크루(가입·크루런·공지·투표·일정·채팅·공동 목표·신고 처리 알림). */
    CREW;

    companion object {
        private val AVATAR_TYPES = setOf("BODY_SHAPE_CHANGED", "BACKGROUND_UNLOCKED")
        private val CREW_TYPES =
            setOf(
                "JOIN_REQUESTED",
                "JOIN_DECIDED",
                "JOIN_EXPIRED",
                "LEADER_TRANSFERRED",
                "CREW_DELETED",
                "KICKED",
                "MANAGER_CHANGED",
                "REPORT_RESOLVED",
                "CREW_RUN_RECRUIT",
                "CREW_RUN_SOON",
                "CREW_NOTICE",
                "CREW_POLL",
                "POLL_CLOSING_SOON",
                "CREW_SCHEDULE",
                "CREW_CHAT",
                "CREW_GOAL_ACHIEVED"
            )

        fun of(type: String?): GatedFeature? = when (type) {
            in AVATAR_TYPES -> AVATAR_CUSTOMIZE
            in CREW_TYPES -> CREW
            else -> null
        }
    }
}
